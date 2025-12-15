package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;
import java.nio.*;

/*
 * What do we do here? We pack in values, resolve symbols, blah blah blah
 */
public class ProgramPIC64 extends ProgramPIC {
	public ProgramPIC64(ExportObject object) {
		super(object);
	}

	public void addSections() {
		/* nothing to do here */
		if (object.getSection(".rdata") == null)
			return;

		program.add( object.getSection(".rdata"), false);

		/* Checking for relocations in .data */
		Iterator i = object.getSection(".rdata").getRelocations().iterator();
		if (i.hasNext()) {
			Relocation reloc = (Relocation)i.next();
			if (reloc.is("x64", Relocation.IMAGE_REL_AMD64_ADDR64))
				throw new RuntimeException(".rdata has relocation for address of symbol '" + reloc.getSymbol().getName() + "'. I can't resolve this from PIC.");
			else
				throw new RuntimeException(".rdata has suspected jump table(s). Compile with -fno-jump-tables (GCC) or equiv. to disable.");
		}
	}
}
