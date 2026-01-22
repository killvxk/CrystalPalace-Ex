package crystalpalace.coff;

import crystalpalace.util.*;
import java.util.*;

public class SectionExport extends Section {
	protected List symbols;

	public SectionExport(String name, byte[] data, List _symbols, List _relocations) {
		this.Name            = name;
		this.RawData         = data;
		this.Characteristics = SectionFlags.getFlags(name);
		this.symbols        = _symbols;
		setRelocations(_relocations);
	}

	public COFFObject getObject() {
		throw new RuntimeException("Don't call this API on SectionExport!");
	}

	public List getSymbols() {
		return new LinkedList(symbols);
	}
}
