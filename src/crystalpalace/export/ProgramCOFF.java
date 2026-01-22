package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;
import java.nio.*;

/*
 * This is an experimental re-implementation of our COFF export to better support transforming BOFs
 */
public class ProgramCOFF {
	protected ExportObject   object;
	protected Map            sections = new LinkedHashMap();
	protected List           linked   = new LinkedList();

	public ProgramCOFF(ExportObject object) {
		this.object = object;
		setupSections();
	}

	public int getSectionNumber(Section s) {
		if ( ".text".equals(s.getName()) )
			return 1;

		if ( ".rdata".equals(s.getName()) )
			return 2;

		if ( ".data".equals(s.getName()) )
			return 3;

		if ( ".bss".equals(s.getName()) )
			return 4;

		throw new RuntimeException("Invalid section: " + s.getName());
	}

	public void createSection(String name, boolean eXecutable) {
		SectionContainer container = new SectionContainer();

		/* add our original section */
		if (object.getSection(name) != null)
			container.add(object.getSection(name), false);

		/* add our linked data */
		Iterator i = object.getExecutableLinks(eXecutable).iterator();
		while (i.hasNext())
			container.add( (Section)i.next(), false );

		/* create our section */
		Section section = container.toSection(name);

		/* create symbols for the linked data by converting their psuedo-section names to symbols */
		Iterator j = object.getExecutableLinks(eXecutable).iterator();
		while (j.hasNext()) {
			Section sect = (Section)j.next();

			String sname  = sect.getName();
			long   svalue = container.getBase(sect);

			if (eXecutable)
				linked.add(Symbol.createFunctionSymbol(section, sname, svalue));
			else
				linked.add(Symbol.createDataSymbol(section, sname, svalue));
		}

		/* add to the mix of our merged COFF, thanks */
		sections.put(name, section);
	}

	public void setupSections() {
		/*
		 * .text section first
		 */
		createSection(".text", true);

		/*
		 * .rdata section next
		 */
		createSection(".rdata", false);

		/*
		 * Our other sections
		 */
		if (object.getSection(".data") != null)
			sections.put(".data", object.getSection(".data"));

		if (object.getSection(".bss") != null)
			sections.put(".bss", object.getSection(".bss"));
	}

	public List getSections() {
		return new LinkedList(sections.values());
	}

	public List getStrings() {
		Set strings = new LinkedHashSet();

		/* our section names are strings */
		strings.addAll(sections.keySet());

		/* our symbol names are part of this... */
		Iterator i = getSymbols().iterator();
		while (i.hasNext()) {
			Symbol temp = (Symbol)i.next();
			strings.add(temp.getName());
		}

		/* yeap, add our symbol name here too */
		Iterator j = getRelocations().iterator();
		while (j.hasNext()) {
			Relocation temp = (Relocation)j.next();
			strings.add(temp.getSymbolName());
		}

		return new LinkedList(strings);
	}

	public List getRelocations() {
		LinkedList results = new LinkedList();

		Iterator i = sections.values().iterator();
		while (i.hasNext()) {
			Section sect = (Section)i.next();
			results.addAll(sect.getRelocations());
		}

		return results;
	}

	public List getSymbols() {
		LinkedList results = new LinkedList();

		/* walk our existing sections */
		Iterator i = sections.values().iterator();
		while (i.hasNext()) {
			Section sect = (Section)i.next();
			results.addAll(sect.getSymbols());
		}

		/* we converted our linked sections to symbols too */
		results.addAll(linked);

		return results;
	}

	public byte[] export() {
		FormatCOFF packer = new FormatCOFF();

		/* create our COFF header */
		packer.header(object.getMachine(), sections.size());

		/* do our section headers next */
		Iterator i = sections.values().iterator();
		while (i.hasNext()) {
			Section sect = (Section)i.next();
			packer.sectionHeader(sect);
		}

		/* I guess we... uhh... do our sections next? */
		Iterator j = sections.values().iterator();
		while (j.hasNext()) {
			Section sect = (Section)j.next();
			packer.sectionData(sect);
		}

		/* lets' do the symbol table now */
		packer.symbolTable(this);

		/* string table next [comes immediately after the symbol table] */
		packer.stringTable(this);

		return packer.getBytes();
	}
}
