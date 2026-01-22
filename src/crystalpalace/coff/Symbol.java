package crystalpalace.coff;

import crystalpalace.util.*;
import java.util.*;

public class Symbol implements Comparable {
	public static final int IMAGE_SYM_CLASS_EXTERNAL  = 2;
	public static final int IMAGE_SYM_CLASS_STATIC    = 3;
	public static final int IMAGE_SYM_CLASS_LABEL     = 6;

	protected Section    section;
	protected String     Name;
	protected long       Value;
	protected int        Type;
	protected int        StorageClass;

	public static Symbol createSectionSymbol(COFFObject obj, Section sect, String name) {
		Symbol temp       = new Symbol();
		temp.section      = sect;
		temp.Name         = name;
		temp.Value        = 0;
		temp.Type         = 0;
		temp.StorageClass = IMAGE_SYM_CLASS_STATIC;
		return temp;
	}

	public static Symbol createDataSymbol(Section sect, String name, long value) {
		Symbol temp       = new Symbol();
		temp.section      = sect;
		temp.Name         = name;
		temp.Value        = value;
		temp.Type         = 0;
		temp.StorageClass = IMAGE_SYM_CLASS_EXTERNAL;
		return temp;
	}

	protected Symbol() {
	}

	public static Symbol createFunctionSymbol(COFFObject obj, String name, long address) {
		return createFunctionSymbol(obj.getSection(".text"), name, address);
	}

	public static Symbol createFunctionSymbol(Section sect, String name, long address) {
		Symbol temp       = new Symbol();
		temp.section      = sect;
		temp.Name         = name;
		temp.Value        = address;
		temp.Type         = 32;
		temp.StorageClass = IMAGE_SYM_CLASS_EXTERNAL;
		return temp;
	}

	public Symbol(COFFObject obj, Section sect, Symbol symb) {
		this.section         = sect;
		this.Name            = symb.Name;
		this.Value           = symb.Value;
		this.Type            = symb.Type;
		this.StorageClass    = symb.StorageClass;
	}

	public Symbol(COFFObject obj, Section sect, COFFWalker.Symbol symb) {
		this.section         = sect;
		this.Name            = symb.getName();
		this.Value           = symb.getValue();
		this.Type            = symb.getType();
		this.StorageClass    = symb.getStorageClass();
	}

	public String getName() {
		return Name;
	}

	public boolean foldsWith(Symbol x) {
		return x.Name.equals(Name) && x.Type == Type && x.StorageClass == StorageClass;
	}

	public boolean isSectionName() {
		return StorageClass == IMAGE_SYM_CLASS_STATIC && Value == 0 && (Name.startsWith(".") || Name.equals( getSection().getName() ));
	}

	public boolean isUndefinedSection() {
		return getSection() == null;
	}

	public Section getSection() {
		return section;
	}

	public int getType() {
		return Type;
	}

	public boolean isExternal() {
		return StorageClass == IMAGE_SYM_CLASS_EXTERNAL;
	}

	public boolean isGlobalVariable() {
		return StorageClass == 2 && !isFunction() && getSection() != null;
	}

	public boolean isFunction() {
		return Type == 32;
	}

	public boolean isLabel() {
		return StorageClass == IMAGE_SYM_CLASS_LABEL;
	}

	public long getValue() {
		return Value;
	}

	public void setValue(long v) {
		Value = v;
	}

	public int compareTo(Object other) {
		return (int)getValue() - (int)((Symbol)other).getValue();
	}

	public String relativeTo(Relocation r) {
		long diff = r.getVirtualAddress() - getValue();

		if (diff > 0) {
			return "<" + getName() + "+" + CrystalUtils.toHex(diff) + ">";
		}
		else if (diff == 0) {
			return "<" + getName() + ">";
		}
		else {
			return "<" + getName() + CrystalUtils.toHex(diff) + ">";
		}
	}

	public long estimateSize() {
		if (getSection() == null)
			return 0;

		Iterator i = getSection().getSymbols().iterator();
		while (i.hasNext()) {
			Symbol temp = (Symbol)i.next();

			/* if it's the NEXT symbol, then it's offset is the end of our data. */
			if (temp.getValue() > getValue())
				return temp.getValue() - getValue();
		}

		/* And, if we are the LAST symbol in our section, then the section size is the end of our data */
		return getSection().getRawData().length - getValue();
	}

	public String toString() {
		if (isFunction()) {
			return "(SYMBOL) " + getName() + "' (func) at " + CrystalUtils.toHex(Value);
		}
		else {
			return "(SYMBOL) " + getName() + "' at " + CrystalUtils.toHex(Value);
		}
	}

	public void toString(int idx, Printer printer) {
		printer.push("Symbol " + idx);
			printer.print("Name",           getName());
			printer.print("Value",          getValue());

			if (getSection() != null) {
				printer.print("Section", getSection().getName());
			}
			else {
				printer.print("Section", "");
			}

			printer.print("Type",           Type);
			printer.print("StorageClass",   StorageClass);

			if (isGlobalVariable() && !isLabel())
				printer.print("Est. Size", estimateSize() + "");

		printer.pop();
	}

}
