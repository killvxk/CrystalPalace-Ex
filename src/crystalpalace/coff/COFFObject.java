/*
 * (c) 2024 Raphael Mudge
 */
package crystalpalace.coff;

import crystalpalace.util.*;
import java.util.*;

/*
 * References:
 * https://courses.cs.washington.edu/courses/cse378/03wi/lectures/LinkerFiles/coff.pdf
 * https://learn.microsoft.com/en-us/windows/win32/debug/pe-format
 */
public class COFFObject {
	protected String Machine;
	protected Map    sections = new LinkedHashMap();
	protected Map    symbols  = new LinkedHashMap();

	public COFFObject(String Machine) {
		this.Machine = Machine;
	}

	public void removeSymbols(Set removeme) {
		Iterator i = symbols.keySet().iterator();
		while (i.hasNext()) {
			String next = (String)i.next();
			if ( removeme.contains(next) )
				i.remove();
		}
	}

	public void remapSymbol(String oldsymb, String newsymb) {
		/* no such symbol here! */
		if (getSymbol(oldsymb) == null)
			return;

		/* Let's do our symbol object itself. */
		if (getSymbol(newsymb) == null) {
			Symbol temp = getSymbol(oldsymb);
			temp.Name = newsymb;
			symbols.put(newsymb, temp);
		}
		/* I'm OK with remapping to an existing symbol, so long as the two are largely similar enough */
		else {
			Symbol olds = getSymbol(oldsymb);
			Symbol news = getSymbol(newsymb);

			if (news.section == null && news.Type == olds.Type && news.Value == 0 && news.StorageClass == 2) {
				olds.Name = newsymb;
				symbols.put(newsymb, olds);
			}
			else if (olds.section != news.section) {
				throw new RuntimeException("Can't remap " + oldsymb + " to existing " + newsymb + " (Section mismatch)");
			}
			else if (olds.Type != news.Type) {
				throw new RuntimeException("Can't remap " + oldsymb + " to existing " + newsymb + " (Type mismatch)");
			}
			else if (olds.StorageClass != news.StorageClass) {
				throw new RuntimeException("Can't remap " + oldsymb + " to existing " + newsymb + " (StorageClass mismatch)");
			}
			else if (olds.Value != news.Value) {
				throw new RuntimeException("Can't remap " + oldsymb + " to existing " + newsymb + " (Value mismatch)");
			}
		}

		/* Now, let's walk ALL of our relocations and fix their symbolnames */
		Iterator i = getSections().values().iterator();
		for (int x = 0; i.hasNext(); x++) {
			Section next = (Section)i.next();
			Iterator j = next.getRelocations().iterator();
			while (j.hasNext()) {
				Relocation reloc = (Relocation)j.next();
				if ( oldsymb.equals(reloc.getSymbolName()) )
					reloc.SymbolName = newsymb;
			}
		}

		/* remove our old symbol! */
		symbols.remove(oldsymb);
	}

	public Symbol getSymbol(String name) {
		return (Symbol)symbols.get(name);
	}

	public Symbol checkPatch(String name, byte[] data) {
		Symbol temp = getSymbol(name);
		if (temp == null)
			throw new RuntimeException("No symbol '" + name + "'");

		Section sect = temp.getSection();

		/* check that our section is initialized, if it's not... throw a tantrum */
		if (sect.isUninitialized())
			throw new RuntimeException("Can't patch symbol " + name + " in uninitialized " + sect.getName() + " section");

		/* estimate the size of the symbol, this is to help sanity check the patch */
		long est = temp.estimateSize();

		/* this is likely a sane and accurate estimate, and maybe a pointer, so we want our patch to fill it
		   in completely */
		if (est == 4 || est == 8) {
			if (est != data.length)
				throw new RuntimeException("Symbol " + name + " (est.) size " + est + "b differs from patch " + data.length + "b size");
		}
		/* for other symbol sizes... we want to just validate that we're not exceeding the estimated size. I've noticed
		 * for oddball global vars (e.g., 40b or something)--the compiler may round up the space allotted for that data
		 * in whatever section. So this is just allowing for that, but checking what we can anyways */
		else {
			if (data.length > est)
				throw new RuntimeException("Symbol " + name + " (est.) size " + est + "b is LESS than patch " + data.length + "b size");
		}

		return temp;
	}

	/* patch the  value of the specified symbol within our COFF with whatever we want */
	public void patch(String name, byte[] data) {
		/* check our patch! */
		Symbol temp = checkPatch(name, data);

		/* OK, now we can do the patch */
		temp.getSection().patch((int)temp.getValue(), data);
	}

	public String getMachine() {
		return Machine;
	}

	public boolean isIntel() {
		return "x64".equals(getMachine()) || "x86".equals(getMachine());
	}

	public int getBits() {
		if ( "x86".equals(getMachine()) )
			return 32;
		else if ( "x64".equals(getMachine()) )
			return 64;
		else
			throw new RuntimeException("Can't get bits for arch '" + getMachine() + "'");
	}

	public Map getSections() {
		return sections;
	}

	public Map getSymbols() {
		return symbols;
	}

	public Section getSection(String name) {
		return (Section)sections.get(name);
	}

	/**
	 * Find entry symbol by matching various naming conventions:
	 * go, _go, __go, and variants with @number suffix (stdcall decoration)
	 *
	 * @return the entry symbol, or null if not found
	 */
	public Symbol findEntrySymbol() {
		String[] prefixes = {"go", "_go", "__go"};

		Iterator i = getSymbols().values().iterator();
		while (i.hasNext()) {
			Symbol s = (Symbol)i.next();
			if (!s.isFunction())
				continue;

			String name = s.getName();
			for (int j = 0; j < prefixes.length; j++) {
				String p = prefixes[j];
				/* exact match or with @number suffix (stdcall) */
				if (name.equals(p) || name.startsWith(p + "@")) {
					return s;
				}
			}
		}
		return null;
	}

	/**
	 * Find entry symbol name from a map of function names.
	 * Returns the first matching entry symbol name, or null if not found.
	 */
	public static String findEntrySymbolName(Map funcs, String machine) {
		String[] prefixes;
		if ("x64".equals(machine)) {
			prefixes = new String[]{"go", "__go"};
		}
		else {
			prefixes = new String[]{"_go", "__go", "go"};
		}

		Iterator i = funcs.keySet().iterator();
		while (i.hasNext()) {
			String name = (String)i.next();
			for (int j = 0; j < prefixes.length; j++) {
				String p = prefixes[j];
				if (name.equals(p) || name.startsWith(p + "@")) {
					return name;
				}
			}
		}
		return null;
	}

	public void toString(Printer printer) {
		printer.print("Machine", getMachine());

		Iterator i = getSymbols().values().iterator();
		for (int x = 0; i.hasNext(); x++) {
			Symbol symb = (Symbol)i.next();
			symb.toString(x, printer);
		}

		Iterator j = getSections().values().iterator();
		for (int x = 0; j.hasNext(); x++) {
			Section sect = (Section)j.next();
			sect.toString(x, printer);
		}
	}

	public String toString() {
		Printer printer = new Printer();
		toString(printer);
		return printer.toString();
	}
}
