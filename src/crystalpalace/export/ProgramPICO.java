package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;
import java.nio.*;

/*
 * PICOs are similar to Cobalt Strike BOFs (but without the API). They are a way to pair COFFs with a tiny loader.
 *
 * Here's the overall pipeline for building/working with PICOs:
 *
 * ProgramPICO - parse a COFF and build our data structures to generate our PICO
 * BuildPICO   - code to generate our PICO and loading directives
 * FormatPICO  - handles the formatting of PICO loading directives
 */
public class ProgramPICO {
	protected ExportObject      object;
	protected SectionContainer  program;
	protected SectionContainer  content;
	protected Imports           imports;
	protected List              patchups;

	/*
	 * Keep track of the nuances of our import table(s) and managing all of that
	 */
	private class Imports {
		protected Map functions  = new HashMap();	// Map(<module>, (Map<function>, <Section>))
		protected Map apis       = new HashMap();	// internal APIs (e.g., LoadLibraryA, GetProcAddress, "import" command stuff)
		protected Map symbols    = new HashMap();	// mapping of relocation symbols to sections

		public Imports() {
		}

		/* (3) Here, we're further processing the symbol to determine if it's DFR (LIB$Func) or an imported API. PICOs
		 * implicitly import GetProcAddress and LoadLibraryA. We store these parsed symbols appropriately to enable the
		 * right thing to happen when we build the PICO loading directives later on. */

		// https://devblogs.microsoft.com/oldnewthing/20060727-00/?p=30343
		protected void trackImport(ParseImport parser, Section s) {
			String module = parser.getModule();
			String func   = parser.getFunction();

			/* If we don't have a module, then we're an (internal) API */
			if ("".equals(module)) {
				apis.put(func, s);
			}
			/* If we do have a module, then we're a Win32 LIB$Func API */
			else {
				if (!functions.containsKey(module)) {
					functions.put(module, new HashMap());
				}

				Map table = (Map)functions.get(module);
				table.put(func, s);
			}
		}

		/* (2) We're now creating a Section object for each symbol and adding it to our content
		 * data structure. This Section object is a "key" to find the offset of that symbol's pointer
		 * entry within our content later. */
		protected void processFunctionRelocation(String symbol, ParseImport parser, Relocation r) {
			/* no need to re-add to our symbol table, it's already there */
			if (hasKey(symbol))
				return;

			/* We need an empty byte array, a placeholder for our function pointer later */
			byte[] placeholder;
			if (r.is("x64", Relocation.IMAGE_REL_AMD64_REL32))
				placeholder = new byte[8];
			else if (r.is("x86", Relocation.IMAGE_REL_I386_DIR32))
				placeholder = new byte[4];
			else
				throw new RuntimeException("Invalid machine!");

			/* create a "Section" to act as a "key" for this function symbol. The key is to our 4/8 byte ptr placeholder */
			Section s = new SectionData(symbol, placeholder);

			/* track it as an API or an imported function */
			trackImport(parser, s);

			/* and... more importantly... add this to our PICO's data */
			content.add( s, false );

			/* track our section by symbol name too */
			symbols.put(symbol, s);
		}

		/* (1) Our entry point. We're walking relocations and looking for imported functions */
		public void setupIAT() {
			Iterator i = program.getRelocations().iterator();
			while (i.hasNext()) {
				Relocation  r      = (Relocation)i.next();
				String      symbol = r.getSymbolName();
				ParseImport parser = new ParseImport(symbol);

				/* ParseImport is checking if we're a reloc symbol prefixed with __imp_ or __imp__ */
				if (parser.isValid())
					processFunctionRelocation(symbol, parser, r);
			}
		}

		public boolean hasKey(String symbol) {
			return symbols.containsKey(symbol);
		}

		public Section getKey(String symbol) {
			return (Section)symbols.get(symbol);
		}
	}

	/*
	 * Self-contained logic to map a Relocation to a container (code, data) and a key to find the offset.
	 * The key can come from multiple sources: a bonafide "remote" section referenced by the reloc, a linked
	 * section, or an imported function which... if we did our job right... will have a slot for the full
	 * address in the data container
	 */
	public class PatchUp {
		protected SectionContainer source    = null;
		protected SectionContainer container = null;
		protected Section          key       = null;
		protected Relocation       reloc     = null;

		public PatchUp(SectionContainer s, Relocation r) {
			this.source = s;
			this.reloc  = r;

			/* check if our relocation refers to data that lives in our eXecutable or data code sections */
			if ( r.getRemoteSection() != null) {
				if ( program.hasOffset(r.getRemoteSection()) ) {
					container = program;
					key       = r.getRemoteSection();
					return;
				}
				else if ( content.hasOffset(r.getRemoteSection()) ) {
					container = content;
					key       = r.getRemoteSection();
					return;
				}
			}

			/* check if it's something that's linked and exists within our content. This is not dependent on a
			 * r.getRemoteSection()=true or false, but I believe it'll usually be true. */
			if ( object.getLinkedSection(r.getSymbolName()) != null) {
				container = content;
				key       = object.getLinkedSection( r.getSymbolName() );

				if ( program.hasOffset(key) )
					container = program;
			}
			/* check if this was handled by our relocation table! */
			else if (imports.hasKey(r.getSymbolName())) {
				container = content;
				key       = imports.getKey(r.getSymbolName());
			}
		}

		public boolean isColocated(SectionContainer target) {
			return target == container;
		}

		public boolean isValid() {
			return container != null && key != null && container.hasOffset(key);
		}

		public int getOffset() {
			return container.getBase(key);
		}

		public int getVirtualAddress() {
			return (int)source.getBase(reloc) + (int)reloc.getVirtualAddress();
		}

		public Relocation getRelocation() {
			return reloc;
		}

		public boolean isSourceCode() {
			return source == program;
		}

		public boolean isSourceData() {
			return source == content;
		}

		public boolean isTargetCode() {
			return container == program;
		}

		public boolean isTargetData() {
			return container == content;
		}
	}

	public ProgramPICO(ExportObject object) {
		this.object    = object;
		this.program   = new SectionContainer();
		this.content   = new SectionContainer();
		this.imports   = new Imports();
		this.patchups  = new LinkedList();
	}

	protected void processRelocation(Relocation r, byte[] rawdata) {
		PatchUp patch = new PatchUp(program, r);

		/* our generic catch-all for a referenced section/symbol we can't process */
		if (!patch.isValid()) {
			throw new RuntimeException("Can't process relocation for " + r.toSimpleString());
		}
		else if (r.is_x64_rel32()) {
			long reladdress = patch.getOffset() - ( r.getVirtualAddress() + r.getFromOffset() );
			int  offset     = r.getRemoteSectionOffset();

			CrystalUtils.putDWORD(rawdata, patch.getVirtualAddress(), (int)reladdress + offset);

			/* This determines whether or not we are dealing with an address relative to what we're patching (the program section) or not. If we're not,
			 * then we need to go back and add a directive to update this patch with the difference between the program and data section. That's all. */
			if (!patch.isColocated(program))
				patchups.add(patch);
		}
		else if (r.is("x86", Relocation.IMAGE_REL_I386_DIR32)) {
			long offsetSymbolFromBase = patch.getOffset();
			int  offsetDataFromSymbol = r.getRemoteSectionOffset();

			Logger.println("Symbol " + r.getSymbolName() + " from base? " + offsetSymbolFromBase + ", data from symbol? " + offsetDataFromSymbol);

			CrystalUtils.putDWORD(rawdata, patch.getVirtualAddress(), (int)offsetSymbolFromBase + offsetDataFromSymbol);

			patchups.add(patch);
		}
		else if (r.is("x86", Relocation.IMAGE_REL_I386_REL32)) {
			long reladdress = patch.getOffset() - ( r.getVirtualAddress() + r.getFromOffset() );
			int  offset     = r.getRemoteSectionOffset();

			CrystalUtils.putDWORD(rawdata, patch.getVirtualAddress(), (int)reladdress + offset);

			/* This determines whether or not we are dealing with an address relative to what we're patching (the program section) or not. If we're not,
			 * then we need to go back and add a directive to update this patch with the difference between the program and data section. That's all. */
			if (!patch.isColocated(program))
				patchups.add(patch);
		}
		else {
			throw new RuntimeException("Can't offline process relocation " + r);
		}
	}

	protected void processDataRelocation(Relocation r, byte[] rawdata) {
		PatchUp patch = new PatchUp(content, r);

		/* our generic catch-all for a referenced section/symbol we can't process */
		if (!patch.isValid()) {
			throw new RuntimeException("Can't process data relocation for " + r.toSimpleString());
		}
		/* do a jump table check... these get QUIRKY... I wasn't able to figure out indirect relocations for x64 jump tables and any .text section relocs
		 * will require an update to the binary transformation framework to account for where the offset address may have shifted to. A whole host of headaches
		 * go away when we just say "I don't support this" */
		else if ( ".text".equals(r.getSymbol().getName()) ) {
			throw new RuntimeException(r.getSection().getName() + " has suspected jump table(s). Compile with -fno-jump-tables (GCC) or equiv. to disable.");
		}
		/* this is an error condition, because there's something I'm missing to make this work as expected, when I try to handle it like the above. */
		else if (r.is_x64_rel32()) {
			throw new RuntimeException(r.getSection().getName() + " has a relative relocation for address of symbol '" + r.getSymbol().getName() + "'. I can't process this for a PICO.");
		}
		else if (r.is("x64", Relocation.IMAGE_REL_AMD64_ADDR64)) {
			long offsetSymbolFromBase = patch.getOffset();
			int  offsetDataFromSymbol = r.getRemoteSectionOffset();

			Logger.println("Symbol " + r.getSymbolName() + " from base? " + offsetSymbolFromBase + ", data from symbol? " + offsetDataFromSymbol);

			CrystalUtils.putDWORD(rawdata, patch.getVirtualAddress(), (int)offsetSymbolFromBase + offsetDataFromSymbol);

			patchups.add(patch);
		}
		else if (r.is("x86", Relocation.IMAGE_REL_I386_DIR32)) {
			long offsetSymbolFromBase = patch.getOffset();
			int  offsetDataFromSymbol = r.getRemoteSectionOffset();

			Logger.println("Symbol " + r.getSymbolName() + " from base? " + offsetSymbolFromBase + ", data from symbol? " + offsetDataFromSymbol);

			CrystalUtils.putDWORD(rawdata, patch.getVirtualAddress(), (int)offsetSymbolFromBase + offsetDataFromSymbol);

			patchups.add(patch);
		}
		else {
			throw new RuntimeException("Can't offline process data relocation " + r);
		}
	}

	public byte[] export() {
		/*
		 * (1) OK, first thing's first... let's add the sections we want.
		 */

		/* (1)(A) Setup our code which is in the "program" section container. We use two containers to allow offsets within each container
		 * to get managed separately. We *really* want this, because PICOs support code and data living in non-contiguous memory. */
		program.add( object.getSection(".text"), false );

		/* add our linked sections */
		Iterator z = object.getExecutableLinks(true).iterator();
		while (z.hasNext()) {
			program.add( (Section)z.next(), false );
		}

		/* (2)(A) Now, we setup our data which is in the "content" container. Really, there are four parts to this single container (which we
		 * presume lives in RW memory. First, there's the IAT which is a pointer-sized slice for each function we import. These can be DFR functions
		 * like MODULE$Function OR internal API stuff (sourced from the struct of function pointers passed to our loader on the C side). This IAT
		 * comes first in our data. Then, we add in .rdata and .data sections. Then, we bring in our linked data. Finally, we bring in the .bss section
		 * at the end. It comes at the end, because as explained, it exists as the delta between our PICO header's stated virtual length and the real
		 * length of the bytes[] we're packing into our PICO package. It's a space-saving hack. But yeap, that's the layout */

		/* we need a table for our imported function addresses too! */
		imports.setupIAT();

		/* add our data and .rdata */
		if (object.getSection(".rdata") != null)
			content.add( object.getSection(".rdata"), false );

		if (object.getSection(".data") != null)
			content.add( object.getSection(".data"), false );

		/* add our linked sections */
		Iterator i = object.getExecutableLinks(false).iterator();
		while (i.hasNext()) {
			content.add( (Section)i.next(), false );
		}

		/*
		 * We're adding .bss at the end and making sure it's the last thing, why? As a space-saving measure...
		 * we send over a size for our final data that's different from the actual packed data we're sent. That
		 * difference in space is our zeroed out .bss section. .bss is unintialized global variables, for those
		 * following at home. If we don't do things this way, what happens is an end-user might link something
		 * from a .spec into this COFF and... if our .bss supposedly came earlier, then we'll have the zero length
		 * (in actuality) bss content get stomped over by the linked data. Putting it at the end makes sure our
		 * .bss offsets/references are always in that empty slack space at the end. That's what's going on here.
		 */
		content.addEmpty( object.getSection(".bss"), false );

		/* (2) Now, let's generate our code and process relocations */
		byte[] code = program.getRawData();

		/* now process relocations against that same raw data... */
		Iterator j = program.getRelocations().iterator();
		while (j.hasNext()) {
			Relocation r = (Relocation)j.next();
			processRelocation(r, code);
		}

		/* (3) Now, let's generate our data and process relocations there */
		byte[] data = content.getRawData();

		Iterator k = content.getRelocations().iterator();
		while (k.hasNext()) {
			Relocation r = (Relocation)k.next();
			processDataRelocation(r, data);
		}

		/*
		 * (3) Now, on with the show, we're building up a packed structure of "directives", parsed by pico.h to
		 * simplify loading and resolving references within our PICO. Following this is the actual .text section
		 * And a packed and padded(? did I make that work) combination of our various linked sections after that.
		 */
		return new BuildPICO(object, this, code, data).export();
	}

	public SectionContainer getCode() {
		return program;
	}

	public SectionContainer getData() {
		return content;
	}

	public List getPatchUps() {
		return patchups;
	}

	public Map getImports() {
		return imports.functions;
	}

	public Map getLocalImports() {
		return imports.apis;
	}
}
