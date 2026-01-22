package crystalpalace.merge;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import java.util.*;

public class COFFList {
	protected List objects = new LinkedList();
	protected Map  groups  = new LinkedHashMap();
	protected Map  symbols = new HashMap();

	public List getGroup(String name) {
		if (!groups.containsKey(name))
			groups.put(name, new LinkedList());

		return (LinkedList)groups.get(name);
	}

	public Map getGroups() {
		return groups;
	}

	public COFFList() {
		getGroup(".text");
		getGroup(".rdata");
		getGroup(".data");
		getGroup(".bss");
	}

	public void add(COFFObject o) {
		/* pull our symbols out of this object, we'll need this later on */
		Iterator i = o.getSections().values().iterator();
		while (i.hasNext()) {
			Section sect = (Section)i.next();

			/* COMDAT check */
			if (foldMe(sect))
				continue;

			/* grabbing symbols now... section by section instead */
			Iterator j    = sect.getSymbols().iterator();
			while (j.hasNext()) {
				Symbol _symbol = (Symbol)j.next();

				if (_symbol.isUndefinedSection() || !_symbol.isExternal())
					continue;

				if (symbols.containsKey(_symbol.getName()))
					throw new RuntimeException("Merge failed. Duplicate symbol " + _symbol.getName());

				symbols.put(_symbol.getName(), _symbol);
			}
		}

		/* track our object as something of interest */
		objects.add(o);
	}

	/*
	 * How does this work? We're checking if a COMDAT section's symbols are already represented in
	 * our current symbol table. If they are (and each symbol is like its other); then we presume
	 * that this section is already fully represented in our merged program and we don't process
	 * the section any further. If we choose to process this section (e.g., don't fold) and there's
	 * a symbol conflict, the presumption is that the error will get raised elsewhere.
	 */
	public boolean foldMe(Section s) {
		if (!s.isCommonData())
			return false;

		Iterator i = s.getSymbols().iterator();
		while (i.hasNext()) {
			Symbol _symbol = (Symbol)i.next();

			if (_symbol.isUndefinedSection() || !_symbol.isExternal())
				continue;

			/* if we don't know about a symbol, don't fold */
			if (! symbols.containsKey(_symbol.getName()) )
				return false;

			Symbol other = (Symbol)symbols.get(_symbol.getName());

			/* if this section is associated with the symbol, do not fold */
			if (_symbol == other)
				return false;

			/* if these two symbols are different in some way... don't fold */
			if (!_symbol.foldsWith(other))
				return false;
		}

		return true;
	}

	public void walk(Section s, Set seen) {
		/* don't walk something that we've already walked and processed! */
		if (seen.contains(s))
			return;

		/* add this section to our seen list, how did I forget that? */
		seen.add(s);

		/* check if we are COMDAT and already present */
		if (foldMe(s))
			return;

		/* track this in our groups! */
		getGroup(s.getGroupName()).add(s);

		/* walk the relocations and add sections related to any of the symbols, thanks! */
		Iterator i = s.getRelocations().iterator();
		while (i.hasNext()) {
			Relocation r = (Relocation)i.next();
			if ( symbols.containsKey(r.getSymbolName()) ) {
				walk( ( (Symbol)symbols.get(r.getSymbolName()) ).getSection(), seen);
			}
			else if (r.getRemoteSection() != null) {
				walk(r.getRemoteSection(), seen);
			}
		}
	}

	public void walkGroup(String n, HashSet seen) {
		Iterator i = objects.iterator();
		while (i.hasNext()) {
			COFFObject obj = (COFFObject)i.next();
			Iterator j = obj.getSections().values().iterator();
			while (j.hasNext()) {
				Section sect = (Section)j.next();

				// This is (presumed) GCC .ident spam. We're going to ignore it in our generic section walk
				//
				// If this is really something important, it'll get brought in when we do our relocation walk
				// in the .text section
				if ( ".rdata$zzz".equals(sect.getName()) )
					continue;

				if ( n.equals(sect.getGroupName()) )
					walk(sect, seen);
			}
		}
	}

	public void finish() {
		HashSet seen = new HashSet();

		/* Quick note: just walking .text appears good enough here. But we're going to keep the walks of these
		 * other sections in place for now */
		walkGroup(".text", seen);
		walkGroup(".rdata", seen);
		walkGroup(".data", seen);
		walkGroup(".bss", seen);
	}
}
