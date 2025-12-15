package crystalpalace.coff;

import crystalpalace.btf.*;
import crystalpalace.export.*;
import crystalpalace.util.*;
import java.util.*;

public class COFFParser implements COFFVisitor {
	protected COFFObject object;
	protected Section    section;
	protected Map        sections = new HashMap();

	public COFFParser() {
	}

	public void visit(COFFWalker.Header header) {
		object = new COFFObject(header.getMachine());
	}

	public void visitOH(byte[] optionalHeader) {
		/* this will always be empty in a COFF context, so we don't care about it */
	}

	public void visit(COFFWalker.Section sect) {
		section = new Section(object, sect);
		object.getSections().put(section.getName(), section);

		/* map our COFFWalker.Section to our working Section object */
		sections.put(sect, section);
	}

	public void visit(COFFWalker.Symbol symbol) {
		Symbol s = new Symbol(object, symbol.getSection() != null ? (Section)sections.get(symbol.getSection()) : null, symbol);

		/* Labels are something MSVC inserts for explicit goto labels. They don't have a unique name, I don't have a use for them, and
		 * I find they confuse everything. So, I'm ignoring them. */
		if (s.isLabel())
			return;

		/*
		 * Symbol names are now guaranteed unique by COFFWalker.Header.resolveSymbolNameConflicts().
		 * Duplicate symbols get a #index suffix appended to their name.
		 */
		object.getSymbols().put(symbol.getName(), s);
	}

	public void visit(COFFWalker.Relocation reloc) {
		Relocation r = new Relocation(section, reloc);
		section.getRelocations().add(r);
	}

	public COFFParser parse(byte[] data) {
		/* this is our CORE parsing logic */
		new COFFWalker().walk(data, this);
		return this;
	}

	public COFFParser parse(ByteWalker walker) {
		new COFFWalker().walk(walker, this);
		return this;
	}

	public COFFObject getObject() {
		return object;
	}

	public void print() {
		System.out.println(object.toString());
	}

	public static void main(String args[]) {
		if (args.length == 0) {
			System.out.println("./coffparse [/path/to/file.o]");
			return;
		}

		try {
			new COFFParser().parse( CrystalUtils.readFromFile(args[0]) ).print();
		}
		catch (java.io.IOException ioex) {
			CrystalUtils.handleException(ioex);
		}
	}
}
