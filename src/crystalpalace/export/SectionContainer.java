package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import java.util.*;

/*
 * This is a sort-of mega-container for our program outputs (whether it's PIC, PIC64, COFF, or a PICO). The goal here is to
 * keep track of multiple "sections" and assist in tracking offsets related to each, gathering symbols, and gathering relocations
 * also related to each.
 */
public class SectionContainer {
	protected Concat     data        = new Concat();
	protected Map        offs        = new LinkedHashMap();
	protected List       sections    = new LinkedList();
	protected int        slack       = 0;

	public SectionContainer() {
	}

	/* add a section to our thing */
	public void add(Section s, boolean alignit) {
		/* a little defensive coding to protect against... me! */
		if (slack > 0)
			throw new RuntimeException("Can't add section " + s.getName() + " after an empty .bss section. Offsets will break!");

		/* add our section! */
		sections.add(s);

		/* track the offset associated with this specific section from this specific COFF object */
		offs.put(s, length());

		/* merge our data */
		data.add(alignit ? s.getData() : s.getRawData());
	}

	/*
	 * This is for our .bss section. It's uninitialized data, that we expect to get initialized to zeroes. Our container
	 * will treat the delta between our overall content and the length() function's return value as the .bss sections.
	 */
	public void addEmpty(Section s, boolean alignit) {
		/* add our section */
		sections.add(s);

		/* track our offset! */
		offs.put(s, length());

		/* we don't want to add to our raw data, BUT we do want to add to the length (at the end of our data). */
		slack += alignit ? s.getData().length : s.getRawData().length;
	}

	public List getRelocations() {
		List results = new LinkedList();

		Iterator i = sections.iterator();
		while (i.hasNext()) {
			Section  sect = (Section)i.next();
			Iterator j    = sect.getRelocations().iterator();
			while (j.hasNext()) {
				Relocation _reloc = (Relocation)j.next();
				results.add(_reloc);
			}
		}

		return results;
	}

	public List getSymbols() {
		List results = new LinkedList();

		Iterator i = sections.iterator();
		while (i.hasNext()) {
			Section  sect = (Section)i.next();
			Iterator j    = sect.getSymbols().iterator();
			while (j.hasNext()) {
				Symbol _symbol = (Symbol)j.next();
				results.add(_symbol);
			}
		}

		return results;
	}

	public Symbol getSymbol(String name) {
		Iterator i = getSymbols().iterator();
		while (i.hasNext()) {
			Symbol symb = (Symbol)i.next();
			if ( name.equals(symb.getName()) )
				return symb;
		}

		return null;
	}

	/**
	 * Find entry symbol by matching various naming conventions:
	 * go, _go, __go, and variants with @number suffix (stdcall decoration)
	 */
	public Symbol findEntrySymbol() {
		String[] prefixes = {"go", "_go", "__go"};

		Iterator i = getSymbols().iterator();
		while (i.hasNext()) {
			Symbol s = (Symbol)i.next();
			if (!s.isFunction())
				continue;

			String name = s.getName();
			for (int j = 0; j < prefixes.length; j++) {
				String p = prefixes[j];
				if (name.equals(p) || name.startsWith(p + "@")) {
					return s;
				}
			}
		}
		return null;
	}

	public boolean hasOffset(Section s) {
		return offs.containsKey(s);
	}

	public int getBase(Relocation r) {
		return getBase(r.getSection());
	}

	public int getBase(Symbol s) {
		return getBase(s.getSection());
	}

	public int getBase(Section sect) {
		return (int)offs.get(sect);
	}

	public byte[] getRawData() {
		return data.get();
	}

	public int length() {
		return data.length() + slack;
	}

	public Section toSection(String name) {
		return new SectionExport(name, getRawData(), getSymbols(), getRelocations());
	}
}
