package crystalpalace.coff;

public interface COFFVisitor {
	public void visit(COFFWalker.Header header);
	public void visit(COFFWalker.Section section);
	public void visit(COFFWalker.Symbol symbol);
	public void visit(COFFWalker.Relocation reloc);
	public void visitOH(byte[] optionalHeader);
}
