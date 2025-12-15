package crystalpalace.coff;

import crystalpalace.btf.*;
import crystalpalace.export.*;
import crystalpalace.util.*;
import java.util.*;

/* Heads and Shoulders... Knees and Toes! This is my COFF Parser. */
public class COFFWalker {
	protected StringTable strings;
	protected ByteWalker  walker;
	protected Symbol      symbols[]  = new Symbol[0];
	protected Section     sections[] = new Section[0];

	/* immediately following the symbol table, we have... the string table */
	/* See: https://courses.cs.washington.edu/courses/cse378/03wi/lectures/LinkerFiles/coff.pdf, pg. 41 */
	public class StringTable {
		protected int    length;
		protected byte[] data;

		public StringTable() {
			/*
			 * "At the beginning of the COFF string table are 4 bytes containing the total size (in bytes) of the rest
			 *  of the string table. This size includes the size field itself, so that the value in this location would be
			 *  4 if no strings were present.
			 *
			 *  Following the size are null-terminated strings pointed to by symbols in the COFF symbol table."
			 *
			 *  Part 5.6 of https://courses.cs.washington.edu/courses/cse378/03wi/lectures/LinkerFiles/coff.pdf
			 */
			length = walker.readInt();
			data   = walker.popBytes(length - 4);
		}

		/* internally, we reference strings at 0 starting AFTER the 4b length value.
		   but, in the COFF, strings are referenced at 0 starting BEFORE the 4b length value */
		public String getStringAt(int index) {
			return _getStringAt(index - 4);
		}

		protected String _getStringAt(int index) {
			try {
				int len = index;
				while (len < data.length) {
					if (data[len] == '\0')
						break;

					len++;
				}

				return new String(data, index, len - index, "UTF-8");
			}
			catch (Exception ex) {
				CrystalUtils.handleException(ex);
				return "";
			}
		}
	}

	public class Symbol {
		protected byte[] Name;
		protected long   Value;
		protected int    SectionNumber;
		protected int    Type;
		protected int    StorageClass;
		protected int    AuxRecords;
		protected String uniqueName = null;  /* set by Header after all symbols are parsed */

		public Symbol() {
			Name                 = walker.popBytes(8);	/* the interpretation of this field is something else for later */
			Value                = walker.readInt();
			SectionNumber        = walker.readShort();
			Type                 = walker.readShort();
			StorageClass         = walker.popByte();
			AuxRecords           = walker.popByte();
		}

		/* set a unique name for this symbol (called by Header when duplicates are detected) */
		public void setUniqueName(String name) {
			this.uniqueName = name;
		}

		/*
		 * OK! So, Crystal Palace expects that each symbol name is globally unique. So far, labels are the problems and here we're
		 * following suit with what xobjdump does and transforming the label symbols to be [Name]-[0xValue] to make them globally
		 * unique. As other situations with uniqueness arise, this is where we'll want to address it. This name value will propagate
		 * as we want everywhere else.
		 */
		public String getName() {
			/* if a unique name was assigned due to duplicate detection, use it */
			if (uniqueName != null) {
				return uniqueName;
			}
			else if ( getSection() != null && StorageClass == crystalpalace.coff.Symbol.IMAGE_SYM_CLASS_STATIC && Value == 0 && _getName().startsWith(".") ) {
				return getSection().getName();
			}
			else if (getSection() != null && getStorageClass() == crystalpalace.coff.Symbol.IMAGE_SYM_CLASS_LABEL) {
				return _getName() + "-" + String.format("%016X", getValue());
			}
			else {
				return _getName();
			}
		}

		/*
		 * Name is:
		 *  (1) a zero-terminated string, up to 8 bytes, OR
		 *  (2) int x = 0, int y = offset into string table with name
		 */
		public String _getName() {
			int x;
			int y;

			ByteWalker walker = new ByteWalker(Name);
			walker.Mark();
			x = walker.readInt();
			y = walker.readInt();
			walker.Return();

			if (x == 0 && y == 0) {
				return "";
			}
			else if (x == 0) {
				return strings.getStringAt(y);
			}
			else {
				return walker.readStringA(8);
			}
		}

		public long getValue() {
			return Value;
		}

		public int getType() {
			return Type;
		}

		public int getStorageClass() {
			return StorageClass;
		}

		public Section getSection() {
			if (SectionNumber >= 1 && SectionNumber <= sections.length) {
				return sections[SectionNumber - 1];
			}

			return null;
		}

		public int getAuxRecords() {
			return AuxRecords;
		}
	}

	public class Relocation {
		protected long    VirtualAddress;
		protected long    SymbolTableIndex;
		protected int     Type;

		public Relocation() {
			VirtualAddress       = walker.readInt();
			SymbolTableIndex     = walker.readInt();
			Type                 = walker.readShort();
		}

		public Symbol getSymbol() {
			return symbols[(int)SymbolTableIndex];
		}

		public long getVirtualAddress() {
			return VirtualAddress;
		}

		public int getType() {
			return Type;
		}
	}

	public class Section {
		protected byte[] 	Name;
		protected long   	VirtualSize;
		protected long   	VirtualAddress;
		protected long   	SizeOfRawData;
		protected long   	PointerToRawData;
		protected long   	PointerToRelocations;
		protected long   	PointerToLinenumbers;
		protected int    	NumberOfRelocations;
		protected int    	NumberOfLinenumbers;
		protected long          Characteristics;
		protected byte[]        RawData;
		protected List          relocations = new LinkedList();

		public Section() {
			Name                 = walker.popBytes(8);
			VirtualSize          = walker.readInt();
			VirtualAddress       = walker.readInt();
			SizeOfRawData        = walker.readInt();
			PointerToRawData     = walker.readInt();
			PointerToRelocations = walker.readInt();
			PointerToLinenumbers = walker.readInt();
			NumberOfRelocations  = walker.readShort();
			NumberOfLinenumbers  = walker.readShort();
			Characteristics      = walker.readInt();

			/* if we're not init, we're probably the .bss section. */
			if (SectionFlags.isUninitialized(Characteristics)) {
				RawData              = new byte[(int)SizeOfRawData];
			}
			/* read the raw data for the section */
			else {
				walker.GoTo((int)PointerToRawData);
				RawData              = walker.getBytes((int)SizeOfRawData);
				walker.Return();
			}

			/* read the relocations */
			walker.GoTo((int)PointerToRelocations);
			for (int x = 0; x < NumberOfRelocations; x++) {
				relocations.add(new Relocation());
			}
			walker.Return();
		}

		public long getVirtualAddress() {
			return VirtualAddress;
		}

		public long getVirtualSize() {
			return VirtualSize;
		}

		public List getRelocations() {
			return relocations;
		}

		public byte[] getRawData() {
			return RawData;
		}

		public long getCharacteristics() {
			return Characteristics;
		}

		public String getName() {
			String name = _getName();

			if (SectionFlags.isCommonData(Characteristics)) {
				return name + "-" + String.format("%016X", PointerToRawData);
			}
			else if (".xdata".equals(name)) {
				return name + "-" + String.format("%016X", PointerToRawData);
			}
			else {
				return name;
			}
		}

		public String _getName() {
			ByteWalker walker = new ByteWalker(Name);

			String _name = walker.readStringA(8);
			if (_name.length() == 0)
				return "";

			if (_name.charAt(0) == '/') {
				String rest = _name.substring(1);
				int    pos  = CrystalUtils.parseInt(rest, -1);
				if (pos == -1)
					return _name;
				return strings.getStringAt(pos);
			}

			return _name;
		}
	}

	public class Header {
		protected int         Machine;
		protected int         NumberOfSections;
		protected long        TimeDateStamp;
		protected long        PointerToSymbolTable;
		protected long        NumberOfSymbols;
		protected int         SizeOfOptionalHeader;
		protected int         Characteristics;
		protected byte[]      OptionalHeader;

		public Header() {
			Machine = walker.readShort();

			/* sanity check the header */
			if (!CrystalUtils.toSet("arm64, x86, x64").contains(getMachine()))
				throw new RuntimeException("COFF starts with unrecognized Machine value " + CrystalUtils.toHex(Machine));

			NumberOfSections     = walker.readShort();
			TimeDateStamp        = walker.readInt();
			PointerToSymbolTable = walker.readInt();
			NumberOfSymbols      = walker.readInt();
			SizeOfOptionalHeader = walker.readShort();
			Characteristics      = walker.readShort();

			/* read our optional header */
			OptionalHeader = walker.getBytes(SizeOfOptionalHeader);

			/* we now have our section table */
			sections = new Section[(int)NumberOfSections];
			for (int x = 0; x < NumberOfSections; x++) {
				sections[x] = new Section();
			}

			/* we now have our symbol table */
			walker.GoTo((int)PointerToSymbolTable);

			symbols = new Symbol[(int)NumberOfSymbols];
			for (int x = 0; x < NumberOfSymbols; x++) {
				symbols[x] = new Symbol();

				/* handle our auxiliary records, but we're just going to drop this information. We don't need them. */
				for (int z = 1; z <= symbols[x].getAuxRecords(); z++) {
					/* parse it */
					new Symbol();

					/* set the value appropriately */
					symbols[x + z] = null;
				}

				/* increment by our number of auxiliary records */
				x += symbols[x].getAuxRecords();
			}

			/* immediately following the symbol table, we have... the string table */
			strings = new StringTable();

			walker.Return();

			/* detect duplicate symbol names and assign unique names */
			resolveSymbolNameConflicts();

			/* if we had errors, then register our dissatisfaction now */
			if (!walker.isSane())
				throw new RuntimeException("Error(s) while parsing COFF. File is possibly malformed.");
		}

		/* detect duplicate symbol names and assign unique names to resolve conflicts */
		protected void resolveSymbolNameConflicts() {
			Map seen = new HashMap();  /* name -> first symbol index */

			for (int x = 0; x < symbols.length; x++) {
				if (symbols[x] == null)
					continue;

				String name = symbols[x].getName();

				if (seen.containsKey(name)) {
					/* conflict detected - rename this symbol with index suffix */
					symbols[x].setUniqueName(name + "#" + x);
				}
				else {
					seen.put(name, new Integer(x));
				}
			}
		}

		public byte[] getOptionalHeader() {
			return OptionalHeader;
		}

		public String getMachine() {
			switch (Machine) {
				case 0x8664:
					return "x64";
				case 0x14c:
					return "x86";
				case 0xaa64:
					return "arm64";
				default:
					return "Unknown " + CrystalUtils.toHex(Machine);
			}
		}
	}

	public COFFWalker() {
	}

	public void walk(ByteWalker walker, COFFVisitor visitor) {
		/* we want our byte walker to be an instance var, we need it */
		this.walker = walker;

		/* start parsing, everything drives off of the header */
		Header header = new Header();

		/* now, we want to start "visiting" each of the components */
		visitor.visit(header);

		/* give the caller a chance to work with the optional header, assuming there is one */
		visitor.visitOH(header.getOptionalHeader());

		/* dump each of our Sections */
		for (int x = 0; x < sections.length; x++) {
			Section sect = sections[x];
			visitor.visit(sect);

			/* dump each of the Relocations affiliated with the section */
			Iterator j = sect.getRelocations().iterator();
			while (j.hasNext()) {
				visitor.visit((Relocation)j.next());
			}
		}

		/* dump ALL of our Symbols */
		for (int x = 0; x < symbols.length; x++) {
			if (symbols[x] != null)
				visitor.visit(symbols[x]);
		}
	}

	public void walk(byte[] content, COFFVisitor visitor) {
		/* start walking.. */
		walk(new ByteWalker(content), visitor);
	}
}
