package crystalpalace.imports;

import crystalpalace.util.*;
import crystalpalace.coff.*;
import crystalpalace.pe.*;
import crystalpalace.export.*;

import java.util.*;

public class ImportsDLL extends Imports {
	protected PEObject object;

	public ImportsDLL(byte[] contents) {
		PEParser parser = new PEParser();
		parser.parse(contents);
		object = parser.getPEObject();
	}

	public List getImports() {
		List result = new LinkedList();

		Iterator i = object.getImports().iterator();
		while (i.hasNext()) {
			PEObject.PEImport entry = (PEObject.PEImport)i.next();

			String mod = entry.getModule();
			if (mod.toLowerCase().endsWith(".dll"))
				mod = mod.substring(0, mod.length() - 4);

			result.add(new Import(mod, entry.getFunction()));
		}

		return result;
	}
}
