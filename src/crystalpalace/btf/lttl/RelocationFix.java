package crystalpalace.btf.lttl;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import com.github.icedland.iced.x86.asm.CodeLabel;

public class RelocationFix {
	protected Relocation reloc;
	public    int        instOffset;
	protected byte[]     valueAt;
	public    CodeLabel  label;
	protected String     toStrOld;

	public RelocationFix(COFFObject object, Relocation reloc, CodeLabel label, int instOffset) {
		this.reloc      = reloc;
		this.label      = label;
		this.instOffset = instOffset;
		this.valueAt    = object.getSection(".text").fetch((int)reloc.getVirtualAddress(), 4);
		this.toStrOld   = reloc.toString();
	}

	public Relocation getRelocation() {
		return reloc;
	}

	public CodeLabel getLabel() {
		return label;
	}

	public int getOffsetFromInstruction() {
		return instOffset;
	}

	public long getRelocOffsetLong() {
		return CrystalUtils.getDWORD(valueAt, 0);
	}

	public byte[] getRelocOffsetValue() {
		return valueAt;
	}

	public void setRelocOffsetValue(long x) {
		CrystalUtils.putDWORD(valueAt, 0, (int)x);
	}

	/* If we report an error based on this relocation, it's going to be POST-mutilation. We cache the
	   pre-mutiliation string representation to give users an error message and information they can
	   follow in the pre-mutiliated disassembly */
	public String toStringOld() {
		return toStrOld;
	}
}
