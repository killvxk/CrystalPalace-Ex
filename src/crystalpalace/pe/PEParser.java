package crystalpalace.pe;

import crystalpalace.export.*;
import crystalpalace.util.*;
import java.util.*;

import crystalpalace.coff.*;

public class PEParser implements PEVisitor {
	protected PEObject peobject = null;

	public PEParser() {
	}

	public void visit(String arch) {
		peobject = new PEObject(arch);
	}

	public void visit(PEWalker.ImageImportDescriptor lib, PEWalker.ImageThunkData func) {
		PEObject.PEImport imp = new PEObject.PEImport();
		imp.address  = func.getAddress();
		imp.module   = lib.getName();
		imp.function = func.getName();
		imp.ordinal  = func.getOrdinal();

		peobject.imports.add(imp);
	}

	public void visit(OptionalHeader ntheaders) {
		peobject.entrypoint = (int)ntheaders.getAddressOfEntryPoint();
	}

	public PEParser parse(byte[] data) {
		new PEWalker().walk(data, this);
		return this;
	}

	public PEObject getPEObject() {
		return peobject;
	}

	public static void main(String args[]) {
		if (args.length == 0) {
			System.out.println("./peparser [/path/to/file.dll]");
			return;
		}

		try {
			String desc = new PEParser().parse(CrystalUtils.readFromFile(args[0])).getPEObject().toString();
			CrystalUtils.print_info(desc);
		}
		catch (Exception ex) {
			CrystalUtils.handleException(ex);
		}
	}
}
