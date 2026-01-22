package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;
import java.nio.*;

/*
 * What do we do here? We pack in values, resolve symbols, blah blah blah
 */
public class ProgramPIC {
	protected SectionContainer program;
	protected ExportObject     object;

	public ProgramPIC(ExportObject object) {
		this.program = new SectionContainer();
		this.object  = object;
	}

	public void checkGoFirst() {
		Symbol symb = program.findEntrySymbol();

		/* Crystal Palace won't report an error if the go symbol doesn't exist */
		if (symb == null)
			return;

		/* check if our symbol is at position 0 of our program */
		if (program.getBase(symb) == 0 && symb.getValue() == 0)
			return;

		/* it's not... so sad... too bad */
		throw new RuntimeException("Entry symbol " + symb.getName() + " is not at position 0. Did your compiler re-order your functions? Compile with -fno-toplevel-reorder (GCC) or equiv. to disable.");
	}

	protected Section getKey(Relocation r) {
		/* The symbol is something external, let's see if it's linked already */
		if ( object.getLinkedSection(r.getSymbolName()) != null) {
			Section s = object.getLinkedSection( r.getSymbolName() );
			if (program.hasOffset(s))
				return s;
		}
		/* Is the relocation referencing something else from its (original) COFF? */
		else if (r.getRemoteSection() != null) {
			if ( program.hasOffset(r.getRemoteSection()) ) {
				return r.getRemoteSection();
			}

			/* Well, that referenced data isn't included in our PIC program, so let's try to give a descriptive error */
			if ( ".rdata".equals(r.getSymbolName()) ) {
				if (object.getSection(".rdata").hasRelocations()) {
					throw new RuntimeException("Can't process relocation for " + r.toSimpleString() + ". Did your compiler insert a jump table? Compile with -fno-jump-tables (GCC) or equiv. to disable.");
				}
				else {
					throw new RuntimeException("Can't process relocation for " + r.toSimpleString() + ". Did you try to use a \"string literal\" from PIC?");
				}
			}
		}

		/* our generic catch-all for a referenced section/symbol we can't process */
		throw new RuntimeException("Can't process relocation for " + r.toSimpleString());
	}

	protected void processRelocation(Relocation r, byte[] rawdata) {
		Section key = getKey(r);

		long reladdress = program.getBase(key) - (r.getVirtualAddress() + r.getFromOffset());
		int  offset     = r.getRemoteSectionOffset();
		int  relocva    = (int)program.getBase(r) + (int)r.getVirtualAddress();

		if (r.is_x64_rel32()) {
			Logger.print_info("Relocation r: " + r);
			CrystalUtils.putDWORD(rawdata, relocva, (int)reladdress + offset);
		}
		/*
		 * NOTE: this is NOT how this relocation is expected to be handled in any other sane way.
		 * But, we're using it as a hack here. We're giving the x86 code the offset to the symbol,
		 * from a specific instruction. It's up to the program to combine this with some other offset
		 * (e.g., the address of the preceeding instruction, obtained with caller(). This is necessary
		 * because x86 does not have a means of loading something from an indirect address.
		 */
		else if (r.is("x86", Relocation.IMAGE_REL_I386_DIR32)) {
			/*
			 * And, how does our program combine this partial pointer with some other offset? We dynamically jam
			 * a function call to caller() into the program and calculate the offset for them. We're not allowing
			 * any other way for now. x86 is too unimportant, this feature is here, and the cognitive overhead
			 * of even thinking about this difference is just too much.
			 */
			if ( object.hasX86Retaddr() ) {
				CrystalUtils.putDWORD(rawdata, relocva, (int)reladdress + offset);
			}
			/* This gets a separate error, because I'd rather the user know they should get rid of their pointer
			 * hacker when updating their .spec file */
			else if ( object.getLinkedSection(r.getSymbolName()) != null ) {
				throw new RuntimeException("Can't process relocation for linked section " + r.getSymbolName() + ". Use 'fixptrs \"_caller\"' in .spec to use this symbol without pointer hacks.");
			}
			else {
				throw new RuntimeException("Can't process relocation for symbol " + r.getReferencedSymbol().getName() + " at " + r.getFunction().relativeTo(r) + ". Use 'fixptrs \"_caller\"' in .spec to make this relocation usable.");
				//CrystalUtils.print_info("Placing a thunk for: " + r.getSymbol().getName() + " Could be? " + r.getReferencedSymbol().getName());
			}
		}
		/*
		 * Our little bit of indirect addressing on x86, specifically for jump/call targets
		 */
		else if (r.is("x86", Relocation.IMAGE_REL_I386_REL32)) {
			CrystalUtils.putDWORD(rawdata, relocva, (int)reladdress + offset);
		}
		else {
			throw new RuntimeException("Can't offline process relocation " + r.toSimpleString());
		}
	}

	/* we do nothing here! */
	public void addSections() {
	}

	public byte[] export() {
		/* add our .text section and sanity check it */
		program.add( object.getSection(".text"), false);
		checkGoFirst();

		/* add other sections, if they're desired */
		addSections();

		/* walk through our linked data and add it to our program data */
		Iterator i = object.getLinks().iterator();
		while (i.hasNext()) {
			program.add( (Section)i.next(), false);
		}

		/* get our rawdata! */
		byte[] data = program.getRawData();

		/* now process relocations against that same raw data... */
		Iterator j = program.getRelocations().iterator();
		while (j.hasNext()) {
			Relocation r = (Relocation)j.next();
			processRelocation(r, data);
		}

		/* voila! we have exported our PIC as-specified in our linker */
		return data;
	}
}
