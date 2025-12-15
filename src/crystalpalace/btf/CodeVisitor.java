package crystalpalace.btf;

/* A quick interface for any walks we do of the instruction set, just keeps it cleaner */
public interface CodeVisitor {
	public void visit(com.github.icedland.iced.x86.Instruction inst);
}
