package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;
import java.nio.*;

/*
 * What do we do here? We pack in values, resolve symbols, blah blah blah
 */
public class ProgramCOFF {
	protected ExportObject   object;
	protected Map            sections = new LinkedHashMap();
	protected Map            sectnos  = new HashMap();
	protected LinkedSections linked   = new LinkedSections();

	public ProgramCOFF(ExportObject object) {
		this.object = object;
		setupSections();
	}

	public int getSectionNumber(Section s) {
		return (int)sectnos.get(s);
	}

	public void add(String name, Section s) {
		sections.put(name, s);
		sectnos.put(s, sectnos.size() + 1);
	}

	public void setupSections() {
		/* the almighty .text section! */
		add(".text",  object.getSection(".text") );

		/* our usual suspects */
		if (object.getSection(".rdata") != null)
			add(".rdata", object.getSection(".rdata"));

		if (object.getSection(".data") != null)
			add(".data", object.getSection(".data"));

		if (object.getSection(".bss") != null)
			add(".bss", object.getSection(".bss"));

		/* add our linked sections */
		linked.go(object);
		add(".cplink", linked.getSection());
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

		/* we converted our linked sections to symbols and folded them into .cplink */
		results.addAll(linked.getSymbols());

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
