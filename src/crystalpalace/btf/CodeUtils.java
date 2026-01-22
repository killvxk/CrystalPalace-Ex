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

	public static boolean is(Instruction instr, String form) {
		return instr.getOpCode().toInstructionString().equals(form);
	}

	public static String f(Instruction instr) {
		GasFormatter formatter = new GasFormatter();
		formatter.getOptions().setDigitSeparator("`");
		formatter.getOptions().setFirstOperandCharIndex(10);
		formatter.getOptions().setGasSpaceAfterMemoryOperandComma​(true);

		StringOutput output = new StringOutput();
		formatter.format(instr, output);

		return String.format("%016X %-32s %-20s", instr.getIP(), instr.getOpCode().toInstructionString(), output);
	}

	public static void p(Instruction instr) {
		CrystalUtils.print_warn(f(instr));
	}

	public static void p(RebuildStep step) {
		if (step.hasRelocation()) {
			CrystalUtils.print_warn(f(step.instruction) + " (" + step.getFunction() + ")\n\t" + step.getRelocation());
		}
		else {
			CrystalUtils.print_warn(f(step.instruction) + " (" + step.getFunction() + ")");
		}
	}

	public static String getRegString(AsmRegister32 reg) {
		return getRegString(reg.get());
	}

	public static String getRegString(AsmRegister64 reg) {
		return getRegString(reg.get());
	}

	public static String getRegString(ICRegister reg) {
		return getRegString(reg.get());
	}

	public static String getRegString(Object reg) {
		if (reg == null)
			return "<null>";
		else if (reg instanceof AsmRegister32)
			return getRegString((AsmRegister32)reg);
		else if (reg instanceof AsmRegister64)
			return getRegString((AsmRegister64)reg);
		else if (reg instanceof ICRegister)
			return getRegString((ICRegister)reg);
		else
			return "getRegString? " + reg.getClass();
	}

	public static String getRegString(int reg) {
		switch (reg) {
			case Register.RAX:
				return "%rax";
			case Register.RCX:
				return "%rcx";
			case Register.RDX:
				return "%rdx";
			case Register.RBX:
				return "%rbx";
			case Register.RSI:
				return "%rsi";
			case Register.RDI:
				return "%rdi";
			case Register.RSP:
				return "%rsp";
			case Register.RBP:
				return "%rbp";
			case Register.RIP:
				return "%rip";
			case Register.R8:
				return "%r8";
			case Register.R9:
				return "%r9";
			case Register.R10:
				return "%r10";
			case Register.R11:
				return "%r11";
			case Register.R12:
				return "%r12";
			case Register.R13:
				return "%r13";
			case Register.R14:
				return "%r14";
			case Register.R15:
				return "%r15";

			case Register.EAX:
				return "%eax";
			case Register.ECX:
				return "%ecx";
			case Register.EDX:
				return "%edx";
			case Register.EBX:
				return "%ebx";
			case Register.ESI:
				return "%esi";
			case Register.EDI:
				return "%edi";
			case Register.ESP:
				return "%esp";
			case Register.EBP:
				return "%ebp";
			case Register.EIP:
				return "%eip";
			case Register.R8D:
				return "%r8d";
			case Register.R9D:
				return "%r9d";
			case Register.R10D:
				return "%r10d";
			case Register.R11D:
				return "%r11d";
			case Register.R12D:
				return "%r12d";
			case Register.R13D:
				return "%r13d";
			case Register.R14D:
				return "%r14d";
			case Register.R15D:
				return "%r15d";

			case Register.AX:
				return "%ax";
			case Register.CX:
				return "%cx";
			case Register.DX:
				return "%dx";
			case Register.BX:
				return "%bx";
			case Register.SI:
				return "%si";
			case Register.DI:
				return "%di";
			case Register.SP:
				return "%sp";
			case Register.BP:
				return "%bp";
			case Register.R8W:
				return "%r8w";
			case Register.R9W:
				return "%r9w";
			case Register.R10W:
				return "%r10w";
			case Register.R11W:
				return "%r11w";
			case Register.R12W:
				return "%r12w";
			case Register.R13W:
				return "%r13w";
			case Register.R14W:
				return "%r14w";
			case Register.R15W:
				return "%r15w";


			case Register.NONE:
				return "NONE";
		}

		return "Uknown_" + reg;
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
}
