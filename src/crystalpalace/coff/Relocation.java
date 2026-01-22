package crystalpalace.coff;

import crystalpalace.util.*;
import java.util.*;

public class Relocation {
	public static final int IMAGE_REL_AMD64_ADDR64  = 0x01;

	public static final int IMAGE_REL_AMD64_REL32   = 0x04;
	public static final int IMAGE_REL_AMD64_REL32_1 = 0x05;
	public static final int IMAGE_REL_AMD64_REL32_2 = 0x06;
	public static final int IMAGE_REL_AMD64_REL32_3 = 0x07;
	public static final int IMAGE_REL_AMD64_REL32_4 = 0x08;
	public static final int IMAGE_REL_AMD64_REL32_5 = 0x09;

	public static final int IMAGE_REL_I386_DIR32    = 0x06;
	public static final int IMAGE_REL_I386_REL32    = 0x14;

	protected Section parent;
	protected long    VirtualAddress;
	protected String  SymbolName;
	protected int     Type;

	public Relocation(Section parent, long VirtualAddress, String SymbolName, int Type) {
		this.parent         = parent;
		this.VirtualAddress = VirtualAddress;
		this.SymbolName     = SymbolName;
		this.Type           = Type;
	}

	public Relocation(Section parent, Relocation reloc) {
		this.parent         = parent;
		this.VirtualAddress = reloc.VirtualAddress;
		this.SymbolName     = reloc.SymbolName;
		this.Type           = reloc.Type;
	}

	public Relocation(Section parent, COFFWalker.Relocation reloc) {
		this.parent         = parent;
		this.VirtualAddress = reloc.getVirtualAddress();
		this.SymbolName     = reloc.getSymbol().getName();
		this.Type           = reloc.getType();
	}

	public COFFObject getObject() {
		return parent.getObject();
	}

	public boolean is_x64_rel32() {
		return getSection().getObject().getMachine().equals("x64") && Type >= 0x04 && Type <= 0x09;
	}

	/* How many bytes, following the relocation Virtual Address, should we use to calculate the offset to whatever the
	 * location refers to? It's implied the relocation rel32 is four bytes + _## */
	public int getFromOffset() {
		/* Oh, genius, some clever one figured out to make the Type values match the address offset thing */
		if (is_x64_rel32())
			return Type;
		else
			return 4;
	}

	public boolean is(String _machine, int _type) {
		return _machine.equals( getSection().getObject().getMachine() ) && Type == _type;
	}

	public boolean isSection(String name) {
		if (name.equals(SymbolName))
			return true;

		if (getRemoteSection() != null && getRemoteSection().getName().equals(name))
			return true;

		return false;
	}

	public Section getRemoteSection() {
		if (getSymbol().isUndefinedSection())
			return null;

		return getSymbol().getSection();
	}

	public int getRemoteSectionOffset() {
		return getOffsetAsLong() + (int)getSymbol().getValue();
	}

	public Section getSection() {
		return parent;
	}

	public Symbol getSymbol() {
		return getSection().getObject().getSymbol(SymbolName);
	}

	public Symbol getReferencedSymbol() {
		if (getSymbol().isSectionName() && getRemoteSection().getGroupName().equals(".text")) {
			Iterator i = getRemoteSection().getSymbols().iterator();
			while (i.hasNext()) {
				Symbol next = (Symbol)i.next();
				if (next.getValue() == getOffsetAsLong())
					return next;
			}

			return getSymbol();
		}
		else {
			return getSymbol();
		}
	}

	public String getSymbolName() {
		return SymbolName;
	}

	public Symbol getFunction() {
		Iterator i = getSection().getSymbols().iterator();

		Symbol last = null;

		while (i.hasNext()) {
			Symbol next = (Symbol)i.next();

			/* We just want function symbols. */
			if (!next.isFunction())
				continue;

			if (next.getValue() >= getVirtualAddress())
				return last;

			last = next;
		}

		return last;
	}

	public int getType() {
		return Type;
	}

	public long getVirtualAddress() {
		return VirtualAddress;
	}

	public int getOffsetAsLong() {
		return CrystalUtils.getDWORD(getSection().getRawData(), (int)getVirtualAddress());
	}

	public void setVirtualAddress(long va) {
		VirtualAddress = va;
	}

	public void setSymbolName(String name) {
		this.SymbolName = name;
	}

	public String toString() {
		return "Relocation(" + Type + ") " + toSimpleString();
	}

	public String toSimpleString() {
		Symbol func = getFunction();
		if (func != null) {
			return getSymbolName() + " @ " + CrystalUtils.toHex(getVirtualAddress()) + " " + func.relativeTo(this);
		}
		else {
			return getSymbolName() + " @ " + CrystalUtils.toHex(getVirtualAddress());
		}
	}

	public void toString(int idx, Printer printer) {
		printer.push("Relocation " + idx);
			if (getFunction() != null) {
				printer.print("VirtualAddress", getVirtualAddress(), getFunction().relativeTo(this));
			}
			else {
				printer.print("VirtualAddress", getVirtualAddress());
			}

			printer.print("Symbol",         getSymbolName());
			printer.print("Offset",         getOffsetAsLong());
			printer.print("Type",           getType());
		printer.pop();
	}
}
