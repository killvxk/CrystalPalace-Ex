package crystalpalace.imports;

import crystalpalace.util.*;
import java.util.*;

public abstract class Imports {
	public static class Import {
		protected String module;
		protected String function;

		public Import(String module, String function) {
			this.module   = module;
			this.function = function;
		}

		public String getModule() {
			return module;
		}

		public String getFunction() {
			return function;
		}

		public String toString() {
			return module.toUpperCase() + "$" + function;
		}
	}

	public abstract List getImports();

	public Set getStrings() {
		Set result = new HashSet();

		Iterator i = getImports().iterator();
		while (i.hasNext()) {
			Import next = (Import)i.next();
			result.add(next.toString());
		}

		return result;
	}

	public static Imports getImports(byte[] contents) {
		ByteWalker peek = new ByteWalker(contents);
		int        magic = peek.readShort();

		/* MZ header, indicating a DLL */
		if (magic == 0x5a4d) {
			return new ImportsDLL(contents);
		}
		/* x64 and x86 Machine values from COFF */
		else if (magic == 0x8664 || magic == 0x14c) {
			return new ImportsCOFF(contents);
		}
		/* we don't know what it is, punt */
		else {
			throw new RuntimeException("Argument is not a COFF or DLL.");
		}
	}

	protected Imports() {
	}
}

