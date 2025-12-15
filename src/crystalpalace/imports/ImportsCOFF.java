package crystalpalace.imports;

import crystalpalace.util.*;
import crystalpalace.coff.*;
import crystalpalace.export.*;

import java.util.*;

public class ImportsCOFF extends Imports {
	protected COFFObject object;

	public ImportsCOFF(byte[] contents) {
		COFFParser parser = new COFFParser();
		parser.parse(contents);
		object = parser.getObject();
	}

	public List getImports() {
		List result = new LinkedList();

		Iterator i = object.getSections().values().iterator();
		while (i.hasNext()) {
			Section temp = (Section)i.next();
			Iterator j = temp.getRelocations().iterator();
			while (j.hasNext()) {
				Relocation  r = (Relocation)j.next();
				String      s = r.getSymbolName();
				ParseImport p = new ParseImport(s);

				if (p.isValid())
					result.add( new Import(p.getModule(), p.getFunction()) );
			}
		}

		return result;
	}
}
