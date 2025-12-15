package crystalpalace.coff;

import crystalpalace.util.*;
import java.util.*;

public class SectionCode extends Section {
	public SectionCode(String name, byte[] data) {
		this.Name            = name;
		this.RawData         = data;
		this.Characteristics = SectionFlags.getFlags(".text");
	}

	public COFFObject getObject() {
		throw new RuntimeException("Don't call this API on SectionCode!");
	}

	public List getRelocations() {
		return new LinkedList();
	}

	public List getSymbols() {
		return new LinkedList();
	}
}
