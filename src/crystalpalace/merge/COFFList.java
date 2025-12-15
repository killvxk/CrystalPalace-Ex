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
		Iterator i = o.getSymbols().values().iterator();
		while (i.hasNext()) {
			Symbol _symbol = (Symbol)i.next();

			if (_symbol.isUndefinedSection() || !_symbol.isExternal())
				continue;

			if (symbols.containsKey(_symbol.getName()))
				throw new RuntimeException("Merge failed. Duplicate symbol " + _symbol.getName());

			symbols.put(_symbol.getName(), _symbol);
		}

		/* track our object as something of interest */
		objects.add(o);
	}

	public void walk(Section s, Set seen) {
		/* don't walk something that we've already walked and processed! */
		if (seen.contains(s))
			return;

		/* add this section to our seen list, how did I forget that? */
		seen.add(s);

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
