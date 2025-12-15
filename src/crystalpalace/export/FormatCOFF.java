package crystalpalace.export;

import crystalpalace.util.*;
import crystalpalace.coff.*;
import java.util.*;

public class FormatCOFF {
	protected Packer     packer  = new Packer();
	protected Locations  locs    = new Locations();

	public FormatCOFF() {
		packer.little();
	}

	public void report(Object key, String subkey) {
		report(key, subkey, packer.size());
	}

	public void report(Object key, String subkey, int value) {
		locs.put(key, subkey, value);
	}

	public void fixLater(Object key, String subkey) {
		locs.fixLater(key, subkey, packer.size());
		packer.addInt(0);
	}

	public void putName(String name) {
		packer.addInt(0);		/* Name, pt. 1 */
		fixLater("stringTable", name);	/* Name, pt. 2 */
	}

	public void putSectionName(String name) {
		if (name.length() <= 7) {
			byte[] temp = CrystalUtils.toUTF8Z(name);
			packer.addBytes(temp);
			packer.pad(8 - temp.length);
		}
		else {
			throw new RuntimeException("Section name " + name + " is >7 chars. Not supported.");
		}
	}

	public static int toMachine(String arch) {
		if ("x86".equals(arch))
			return 0x014c;
		else if ("x64".equals(arch))
			return 0x8664;
		else if ("arm64".equals(arch))
			return 0xaa64;
		else
			throw new RuntimeException("Invalid arch " + arch);
	}

	public void header(String arch, int sectionCount) {
		packer.addUShort(toMachine(arch));	/* Machine */
		packer.addUShort(sectionCount);		/* NumberOfSections */
		packer.addInt(0);			/* TimeDateStamp */
		fixLater("", "PointerToSymbolTable");	/* PointerToSymbolTable */
		fixLater("", "NumberOfSymbols");	/* NumberOfSymbols */
		packer.addUShort(0);    	 	/* SizeOfOptionalHeader */
		packer.addUShort(0x4);     		/* Characteristics */
	}

	public void relocation(Relocation reloc) {
		packer.addInt((int)reloc.getVirtualAddress());	/* VirtualAddress */
		fixLater(reloc, "SymbolTableIndex");		/* SymbolTableIndex */
		packer.addUShort(reloc.getType());		/* Type */
	}

	public void sectionHeader(Section sect) {
		byte[] rawdata = sect.getRawData();
		List   relocs  = sect.getRelocations();

		/* do our section header */
		putSectionName(sect.getName());			/* Name */
		packer.addInt(0);				/* VirtualSize */
		packer.addInt(0);				/* VirtualAddress */
		packer.addInt(rawdata.length);			/* SizeOfRawData */

		if (sect.isUninitialized()) {
			packer.addInt(0);
			packer.addInt(0);

			if (relocs.size() > 0)
				throw new RuntimeException("Section " + sect.getName() + " is not init (like .bss); but has relocations? HOW?!?");
		}
		else {
			fixLater(sect, "PointerToRawData");	/* PointerToRawData */
			fixLater(sect, "PointerToRelocations");	/* PointerToRelocations */
		}

		packer.addInt(0);				/* PointerToLinenumbers */
		packer.addUShort(relocs.size());		/* NumberOfRelocations */
		packer.addUShort(0);				/* NumberOfLinenumbers */
		packer.addInt((int)sect.getCharacteristics());	/* Characteristics */
	}

	public void sectionData(Section sect) {
		String name    = sect.getName();
		byte[] rawdata = sect.getRawData();
		List   relocs  = sect.getRelocations();

		/* if this is a .bss section, we have nothing to do here */
		if (sect.isUninitialized())
			return;

		/* do our relocations now */
		report(sect, "PointerToRelocations");
		Iterator i = relocs.iterator();
		while (i.hasNext()) {
			Relocation reloc = (Relocation)i.next();
			relocation(reloc);
		}

		/* add our raw data */
		report(sect, "PointerToRawData");
		packer.addBytes(rawdata);
	}

	public void symbol(String name, long value, int sectionNumber, int type, int sclass) {
		putName(name);				/* Name */
		packer.addInt((int)value);		/* Value */
		packer.addUShort(sectionNumber);	/* SectionNumber */
		packer.addUShort(type);			/* Type */
		packer.addByte((byte)sclass);		/* StorageClass */
		packer.addByte((byte)0);		/* # of Aux Symbols */
	}

	public void symbolTable(ProgramCOFF exe) {
		Iterator i;
		int      count = 0;

		Map      offs = new HashMap();

		report("", "PointerToSymbolTable");

		/* bring in our sections as symbols */
		i = exe.getSections().iterator();
		while (i.hasNext()) {
			Section _sect = (Section)i.next();
			symbol(_sect.getName(), 0x0, exe.getSectionNumber(_sect), 0, Symbol.IMAGE_SYM_CLASS_STATIC);
			offs.put(_sect.getName(), count);
			count++;
		}

		/* do the symbol work, fuck this shit */
		i = exe.getSymbols().iterator();
		while (i.hasNext()) {
			Symbol _symbol = (Symbol)i.next();
			symbol(_symbol.getName(), _symbol.getValue(), exe.getSectionNumber(_symbol.getSection()), _symbol.getType(), Symbol.IMAGE_SYM_CLASS_EXTERNAL);
			offs.put(_symbol.getName(), count);
			count++;
		}

		/* bring in our relocations as symbols */
		i = exe.getRelocations().iterator();
		while (i.hasNext()) {
			Relocation _reloc = (Relocation)i.next();

			if ( offs.containsKey(_reloc.getSymbolName()) ) {
				report(_reloc, "SymbolTableIndex", (int)offs.get(_reloc.getSymbolName()) );
			}
			else {
				report(_reloc, "SymbolTableIndex", count);
				symbol(_reloc.getSymbolName(), 0x0, 0x0, 0x0, Symbol.IMAGE_SYM_CLASS_EXTERNAL);
				offs.put(_reloc.getSymbolName(), count);
				count++;
			}
		}

		/* report how many symbols we have, thanks */
		report("", "NumberOfSymbols", count);
	}

	public void stringTable(ProgramCOFF exe) {
		Concat strings = new Concat();

		/* build our string table and note the string indices */
		Iterator i = exe.getStrings().iterator();
		while (i.hasNext()) {
			String temp = (String)i.next();
			report("stringTable", temp, strings.length() + 4);
			strings.add(CrystalUtils.toUTF8Z(temp));
		}

		/* generate our header */
		packer.addInt(strings.length() + 4);
		packer.addBytes(strings.get());
	}

	public byte[] getBytes() {
		byte[] temp = packer.getBytes();
		locs.patch(temp);
		return temp;
	}
}
