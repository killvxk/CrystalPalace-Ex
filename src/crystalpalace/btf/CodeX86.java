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

public class CodeX86 extends Code {
	public CodeX86(COFFObject o) {
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
			if (_offsets.hasDisplacement() || _offsets.hasImmediate() || _offsets.hasImmediate2())
				offsets.put(instr, _offsets);

			instructions.add(instr);

		}

		return instructions;
	}

	/* get the relocation associated with a specific instruction */
	public Relocation getRelocation(Instruction instr) {
		if (hasOffsets(instr))
			return (Relocation)relocs.get(getRelocationAddress(instr));
		else
			return null;
	}

	public SymbolResult relocationToSymbol(Relocation reloc, int operand, long address) {
		/*
		 * Code smell? Can we have two relocations per instruction on x86? Can they be at different operands?
		 * I don't know. Something to be aware of. Fortunately, this is JUST our disassembly printing code and
		 * it doesn't matter as much if this isn't 100% accurate. But, something I wanted to note here.
		 */

		if (operand == 0) {
			if (".text".equals(reloc.getSymbolName())) {
				Symbol temp = getLabel(address);
				if (temp != null)
					return new SymbolResult(address, temp.getName());
			}

			return new SymbolResult(0, reloc.getSymbolName());
		}

		return null;
	}

	/* helper to get the address of our relocation address (where the bytes we patches go) */
	private long getRelocationAddress(Instruction instr) {
		ConstantOffsets offsets = getOffsets(instr);

		if (offsets.hasDisplacement()) {
			long cand = instr.getIP() + offsets.displacementOffset;
			if (relocs.containsKey(cand))
				return cand;
		}

		if (offsets.hasImmediate()) {
			long cand = instr.getIP() + offsets.immediateOffset;
			if (relocs.containsKey(cand))
				return cand;
		}

		if (offsets.hasImmediate2()) {
			long cand = instr.getIP() + offsets.immediateOffset2;
			if (relocs.containsKey(cand))
				return cand;
		}

		return instr.getIP();
	}
}
