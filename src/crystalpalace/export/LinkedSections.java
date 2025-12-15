package crystalpalace.export;

import crystalpalace.util.*;
import crystalpalace.coff.*;

import java.util.*;

public class LinkedSections {
	protected Section      section = null;
	protected List         symbols = new LinkedList();

	public LinkedSections() {
	}

	public Section getSection() {
		return section;
	}

	public LinkedSections go(ExportObject object) {
		SectionContainer container = new SectionContainer();

		/*
		 * Add all of our linked sections to our container!
		 */

		/* If we already have a .cplink section, let's get its contents and incorporate them here */
		Section old = object.getSection(".cplink");
		if (old != null)
			container.add(old, false);

		/* add our sections to a global container thing */
		Iterator i = object.getLinks().iterator();
		while (i.hasNext()) {
			Section sect = (Section)i.next();
			container.add(sect, false);
		}

		/* create our section, please */
		section = new SectionData(".cplink", container.getRawData());

		/*
		 * Create our symbols now!
		 */

		/* If we already have a .cplink section, let's get its symbols and incorporate them here */
		if (old != null) {
			Iterator k = old.getSymbols().iterator();
			while (k.hasNext()) {
				Symbol temp = (Symbol)k.next();

				String name  = temp.getName();
				long   value = container.getBase(old) + temp.getValue();

				symbols.add(Symbol.createDataSymbol(section, name, value));
			}
		}

		/* walk the sections again and create a symbol for each */
		Iterator j = object.getLinks().iterator();
		while (j.hasNext()) {
			Section sect = (Section)j.next();

			String name  = sect.getName();
			long   value = container.getBase(sect);

			symbols.add(Symbol.createDataSymbol(section, name, value));
		}

		return this;
	}

	public List getSymbols() {
		return symbols;
	}
}
