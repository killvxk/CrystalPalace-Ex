package crystalpalace.btf;

import crystalpalace.btf.Code;

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

public class CodeX64 extends Code {
	public CodeX64(COFFObject o) {
		super(o);
	}

	public List disassemble() {
		/* disassemble the .text section */
		ByteArrayCodeReader codeReader = new ByteArrayCodeReader(code);
		Decoder             decoder    = new Decoder(object.getBits(), codeReader);
		decoder.setIP(startip);

		long endRip = decoder.getIP() + code.length;

		List instructions = new LinkedList();
		while (decoder.getIP() < endRip) {
			Instruction     instr    = decoder.decode();
			ConstantOffsets _offsets = decoder.getConstantOffsets(instr);

			/* store our offsets for memory addresses and other stuff in the instruction */
			if (_offsets.hasDisplacement()) {
				offsets.put(instr, _offsets);
			}
			/* this is a special case, normally shouldn't be a reloc but it indicates an unresolved local symbol */
			else if ("CALL rel32".equals(instr.getOpCode().toInstructionString()) && _offsets.hasImmediate()) {
				offsets.put(instr, _offsets);
			}

			instructions.add(instr);

		}

		return instructions;
	}

	public SymbolResult relocationToSymbol(Relocation reloc, int operand, long address) {
		return new SymbolResult(reloc.getVirtualAddress() + reloc.getFromOffset(), reloc.getSymbolName());
	}

	/* get the relocation associated with a specific instruction */
	public Relocation getRelocation(Instruction instr) {
		if (hasOffsets(instr))
			return (Relocation)relocs.get(getRelocationAddress(instr));
		else
			return null;
	}

	/* helper to get the address of our relocation address (where the bytes we patches go) */
	private long getRelocationAddress(Instruction instr) {
		ConstantOffsets offsets = getOffsets(instr);
		if (offsets != null) {
			if (offsets.hasDisplacement())
				return instr.getIP() + offsets.displacementOffset;
			else if (offsets.hasImmediate())
				return instr.getIP() + offsets.immediateOffset;
			else
				throw new RuntimeException("getRelocationAddress('" + CodeUtils.toString(instr) + "') arg has offsets but not displ/immed.");
		}
		else {
			throw new RuntimeException("getRelocationAddress('" + CodeUtils.toString(instr) + "') arg has no displacement");
		}
	}
}
