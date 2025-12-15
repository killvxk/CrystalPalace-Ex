package crystalpalace.btf;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;
import com.github.icedland.iced.x86.fmt.fast.*;
import com.github.icedland.iced.x86.info.*;

/*
 * This collection of utilities is MAINLY around printing our disassembled instructions in different ways. It's to help
 * me with validating the results of our analyze, modify, rebuild pipeline. Maybe generate some output for you, if I
 * choose to do that. But, this code is messy and ugly, but necessary to develop this kind of functionality.
 */
public class CodeUtils {
	private static final int HEXBYTES_COLUMN_BYTE_LENGTH = 10;

	public static void details(Code code, String msg, Instruction instr) {
		CrystalUtils.print_stat(msg + ":");
		System.out.println(instr.getOpCode().toInstructionString());
		printInst(code, instr);
		dump(instr);
		System.out.println("---");
	}

	public static String toString(Instruction instr) {
		return String.format("%016X %s", instr.getIP(), instr.getOpCode().toInstructionString());
	}

	public static void p(Instruction instr) {
		CrystalUtils.print_warn(String.format("%016X %s", instr.getIP(), instr.getOpCode().toInstructionString()));
	}

	public static void dump(Instruction instr) {
		InstructionInfoFactory instrInfoFactory = new InstructionInfoFactory();
		System.out.println(String.format("%016X %s", instr.getIP(), instr));

		OpCodeInfo opCode = instr.getOpCode();
		InstructionInfo info = instrInfoFactory.getInfo(instr);
		FpuStackIncrementInfo fpuInfo = instr.getFpuStackIncrementInfo();
		System.out.println(String.format("    OpCode: %s", opCode.toOpCodeString()));
		System.out.println(String.format("    Instruction: %s", opCode.toInstructionString()));

		if (instr.isStackInstruction())
			System.out.println(String.format("    SP Increment: %d", instr.getStackPointerIncrement()));

		for (int i = 0; i < instr.getOpCount(); i++) {
			int opKind = instr.getOpKind(i);
			if (opKind == OpKind.MEMORY) {
				int size = MemorySize.getSize(instr.getMemorySize());
				if (size != 0)
					System.out.println(String.format("    Memory size: %d", size));
				break;
			}
			else if (opKind == OpKind.REGISTER) {
				System.out.println("Op " + i + " is a register");
			}
		}
	}

	public static void printInst(Code code, Instruction inst) {
		List temp = new LinkedList();
		temp.add(inst);
		print(code, temp);
	}

	public static void print(Code code, List instructions) {
		print(System.out, code, instructions);
	}

	public static void print(java.io.PrintStream out, Code code) {
		print(out, code, code.getCode());
	}

	public static void print(java.io.PrintStream out, Code code, List instructions) {
		SymbolResolver symResolver = new SymbolResolver() {
			public SymbolResult getSymbol(Instruction instruction, int operand, int instructionOperand, long address, int addressSize) {
					/* check if we have a relocation */
					Relocation reloc = code.getRelocation(instruction);

					if (reloc != null) {
						return code.relocationToSymbol(reloc, operand, address);
					}

					/* check if we're calling a label */
					if ("CALL rel32".equals( instruction.getOpCode().toInstructionString() )) {
						if (code.getLabel(address) != null)
							return new SymbolResult(address, code.getLabel(address).getName());
					}
					else if (instruction.isIPRelativeMemoryOperand()) {
						if ("LEA r64, m".equals( instruction.getOpCode().toInstructionString() )) {
							if (code.getLabel(address) != null)
								return new SymbolResult(address, code.getLabel(address).getName());
						}
						else if ("MOV r64, r/m64".equals(instruction.getOpCode().toInstructionString()) ) {
							if (code.getLabel(address) != null)
								return new SymbolResult(address, code.getLabel(address).getName());
						}
						else if ( "CALL r/m64".equals(instruction.getOpCode().toInstructionString()) ) {
							if (code.getLabel(address) != null)
								return new SymbolResult(address, code.getLabel(address).getName());
						}
					}


					return null;
			}
		};

		/* setup our output formatter */
		GasFormatter formatter = new GasFormatter(symResolver);
		formatter.getOptions().setDigitSeparator("`");
		formatter.getOptions().setFirstOperandCharIndex(10);
		formatter.getOptions().setGasSpaceAfterMemoryOperandComma​(true);

		/* now we're going to walk the instructions and format them */
		StringOutput output = new StringOutput();
		Iterator iter = instructions.iterator();
		while (iter.hasNext()) {
			Instruction instr = (Instruction)iter.next();

			if ( code.getLabel(instr.getIP()) != null) {
				out.println("");
				out.println(String.format("%016X", instr.getIP()) + " <" + code.getLabel(instr.getIP()).getName() + ">:");
			}

			// Don't use instr.toString(), it allocates more, uses masm syntax and default options
			formatter.format(instr, output);
			out.print(String.format("%016X", instr.getIP()));
			out.print(" ");

			int instrLen = instr.getLength();
			int byteBaseIndex = (int)(instr.getIP() - 0x0);
			for (int i = 0; i < instrLen; i++)
				out.print(String.format("%02X", code.getCodeAsBytes()[byteBaseIndex + i]));

			int missingBytes = HEXBYTES_COLUMN_BYTE_LENGTH - instrLen;
			for (int i = 0; i < missingBytes; i++)
				out.print("  ");

			out.print(" ");
			out.println(output.toStringAndReset());
		}
	}

	public static void print(Code code) {
		print(code, code.getCode());
	}

	public static int compare(byte[] str1, byte[] str2, int a, int b) {
		int count = 0;

		while (true) {
			if ((count + a) >= str1.length || (count + b) >= str2.length)
				break;

			if (str1[count + a] != str2[count + b])
				break;

			count++;
		}

		return count;
	}

	public static int lcs(byte[] str1, byte[] str2) {
		int m   = str1.length;
		int n   = str2.length;

		int idxm   = 0;
		int idxn   = 0;
		int max    = 0;

		int lens[][] = new int[m][n];

		for (int x = 0; x < str1.length; x++) {
			for (int y = 0; y < str2.length; y++) {
				lens[x][y] = compare(str1, str2, x, y);
			}
		}

		for (int x = 0; x < str1.length; x++) {
			for (int y = 0; y < str2.length; y++) {
				if (lens[x][y] >= max) {
					idxm = x;
					idxn = y;
					max  = lens[x][y];
				}
			}
		}

		CrystalUtils.print_stat("Max LCS is: " + max + " @ indices " + String.format("%016X", idxm) + ", " + String.format("%016X", idxn));

		return max;
	}
}
