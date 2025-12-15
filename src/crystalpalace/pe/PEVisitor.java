package crystalpalace.pe;

import crystalpalace.coff.COFFObject;

public interface PEVisitor {
	public void visit(String arch);
	public void visit(PEWalker.ImageImportDescriptor lib, PEWalker.ImageThunkData func);
	public void visit(OptionalHeader ntheaders);
}
