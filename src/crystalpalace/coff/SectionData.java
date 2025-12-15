package crystalpalace.coff;

import crystalpalace.util.*;
import java.util.*;

public class SectionData extends Section {
	public SectionData(String name, byte[] data) {
		this.Name            = name;
		this.RawData         = data;
		this.Characteristics = SectionFlags.getFlags(".rdata");
	}

	public COFFObject getObject() {
		throw new RuntimeException("Don't call this API on SectionData!");
	}

	public List getRelocations() {
		return new LinkedList();
	}

	public List getSymbols() {
		return new LinkedList();
	}
}
