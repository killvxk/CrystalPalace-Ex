package crystalpalace.coff;

import crystalpalace.util.*;
import java.util.*;

public class Section {
	protected COFFObject object;
	protected String     Name;
	protected byte[]     RawData;
	protected long       Characteristics;
	protected List       relocations = new LinkedList();

	public Section() {
	}

	public Section(COFFObject obj, String name) {
		this.object          = obj;
		this.Name            = name;
		this.Characteristics = SectionFlags.getFlags(name);
	}

	public Section(COFFObject obj, COFFWalker.Section sect) {
		this.object          = obj;
		this.Name            = sect.getName();
		this.RawData         = sect.getRawData();
		this.Characteristics = sect.getCharacteristics();
	}

	public COFFObject getObject() {
		return object;
	}

	public String getGroupName() {
		String name = getName();
		if (name.indexOf('$') != -1)
			name = name.substring(0, name.indexOf('$'));

		return name;
	}

	public String getName() {
		return Name;
	}

	public void setData(byte[] rawd) {
		RawData = rawd;
	}

	/* returns our data, aligned to a 4KB page size */
	public byte[] getData() {
		int    rawlen = RawData.length;
		int    padlen = 4096 - (rawlen % 4096);

		byte[] temp   = new byte[rawlen + padlen];
		for (int x = 0; x < RawData.length; x++) {
			temp[x] = RawData[x];
		}

		return temp;
	}

	/* we're going to clue off the characteristics value here, but really this is
	 * a check for the .bss section */
	public boolean isUninitialized() {
		return SectionFlags.isUninitialized(Characteristics);
	}

	public boolean isExecutable() {
		return SectionFlags.isExecute(Characteristics);
	}

	/* we may need this elsewhere, right? */
	public long getCharacteristics() {
		return Characteristics;
	}

	public int getPadLength() {
		return 4096 - (RawData.length % 4096);
	}

	/* returns our data without the aligning */
	public byte[] getRawData() {
		return RawData;
	}

	/* patch the specified byte array into our section raw data, thanks */
	public void patch(int offset, byte[] patch) {
		for (int x = 0; x < patch.length; x++) {
			RawData[x + offset] = patch[x];
		}
	}

	/* fetch a specific part of our raw data */
	public byte[] fetch(int offset, int length) {
		byte[] temp = new byte[length];
		for (int x = 0; x < temp.length; x++) {
			temp[x] = RawData[x + offset];
		}
		return temp;
	}

	public List getRelocations() {
		return relocations;
	}

	public boolean hasRelocations() {
		return getRelocations().size() > 0;
	}

	public List getSymbols() {
		List symbols = new LinkedList();

		/* find all of the symbols associated with this section */
		Iterator i = getObject().getSymbols().values().iterator();
		while (i.hasNext()) {
			Symbol next = (Symbol)i.next();
			if (next.getSection() == this && !next.isSectionName())
				symbols.add(next);
		}

		/* sort them! */
		Collections.sort(symbols);

		return symbols;
	}

	public void setRelocations(List r) {
		relocations = r;
	}

	public String toString() {
		return "Section " + getName() + ", " + RawData.length + "b, " + SectionFlags.toString(getCharacteristics());
	}

	public void toString(int idx, Printer printer) {
		printer.push("Section " + idx);
			printer.print("Name",            getName());
			printer.print("SizeOfRawData",   RawData.length);
			printer.print("Characteristics", SectionFlags.toString(getCharacteristics()));

			Iterator i = getSymbols().iterator();
			for (int x = 0; i.hasNext(); x++) {
				Symbol next = (Symbol)i.next();
				next.toString(x, printer);
			}

			Iterator j = getRelocations().iterator();
			for (int x = 0; j.hasNext(); x++) {
				Relocation next = (Relocation)j.next();
				next.toString(x, printer);
			}
		printer.pop();
	}

}
