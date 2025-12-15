package crystalpalace.pe;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import java.util.*;

public class PEObject {
	protected List       imports    = new LinkedList();
	protected String     arch       = "";
	protected int        entrypoint = 0;

	public static class PEImport {
		protected int     address;
		protected String  module;
		protected String  function;
		protected int     ordinal;

		public PEImport() {
		}

		public int getAddress() {
			return address;
		}

		public String getModule() {
			return module;
		}

		public String getFunction() {
			return function;
		}

		public int getOrdinal() {
			return ordinal;
		}

		public boolean isOrdinal() {
			return function == null;
		}

		public String toString() {
			if (isOrdinal())
				return "Import " + CrystalUtils.toHex(getAddress()) + " " + getModule() + "$(#" + getOrdinal() + ")";
			else
				return "Import " + CrystalUtils.toHex(getAddress()) + " " + getModule() + "$" + getFunction();
		}
	}

	public int getEntryPoint() {
		return entrypoint;
	}

	public List getImports() {
		return imports;
	}

	public PEObject(String arch) {
		this.arch = arch;
	}

	public String getMachine() {
		return arch;
	}

	public void toString(Printer printer) {
		printer.print("PE");
		printer.print("Machine", getMachine());
		printer.print("EntryPoint", getEntryPoint());
		printer.push("Imports");

		Iterator i = getImports().iterator();
		while (i.hasNext()) {
			printer.print(i.next().toString());
		}
		printer.pop();
	}

	public String toString() {
		Printer printer = new Printer();
		toString(printer);
		return printer.toString();
	}
}
