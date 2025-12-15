package crystalpalace.merge;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import crystalpalace.export.*;

import java.util.*;

public class COFFMerge {
	protected COFFObject object  = null;
	protected COFFList   objects = new COFFList();

	protected Map        containers = new HashMap();

	public COFFMerge() {
	}

	public SectionContainer getContainer(String name) {
		if (!containers.containsKey(name))
			containers.put(name, new SectionContainer());

		return (SectionContainer)containers.get(name);
	}

	public void merge(COFFObject mergeme) {
		if (object == null)
			object = new COFFObject(mergeme.getMachine());

		objects.add(mergeme);
	}


	/* find the container for this specific symbol */
	protected Map.Entry findContainer(Section s) {
		Iterator i = containers.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry        entry = (Map.Entry)i.next();
			SectionContainer next  = (SectionContainer)entry.getValue();

			if (next.hasOffset(s))
				return entry;
		}

		return null;
	}

	/* Getting rid of relocations that map to symbols in our current section. MSVC likes to do this for everything,
	 * but COFF merging creates this situation too */
	protected void resolveRelocations(String sectname) {
		SectionContainer container = getContainer(sectname);
		Section          section   = object.getSection(sectname);

		Iterator i = section.getRelocations().iterator();
		while (i.hasNext()) {
			Relocation reloc = (Relocation)i.next();

			if (reloc.getRemoteSection() != section)
				continue;

			int  relocva     = (int)reloc.getVirtualAddress();
			int  dataaddr    = reloc.getOffsetAsLong() + (int)reloc.getSymbol().getValue();
			long reladdress  = reloc.getVirtualAddress() + reloc.getFromOffset();

			/* what does it take to resolve this relocation, with our specific symbol? */
			if (reloc.is_x64_rel32()) {
				CrystalUtils.putDWORD(section.getRawData(), relocva, dataaddr - (int)reladdress);
				i.remove();
			}
			else if (reloc.is("x86", Relocation.IMAGE_REL_I386_REL32)) {
				CrystalUtils.putDWORD(section.getRawData(), relocva, dataaddr - (int)reladdress);
				i.remove();
			}
		}
	}

	protected void createRelocations(String sectname) {
		SectionContainer container = getContainer(sectname);
		Section          section   = object.getSection(sectname);

		Iterator i = container.getRelocations().iterator();
		while (i.hasNext()) {
			Relocation oldreloc = (Relocation)i.next();
			Relocation newreloc = new Relocation(section, oldreloc);

			/* let's work on the virtual address of the relocation */
			long  virtaddr   = container.getBase(oldreloc) + oldreloc.getVirtualAddress();
			newreloc.setVirtualAddress(virtaddr);

			/* IF we're by name referring to a remote section that exists, we need and want to do some gymnastics to update
			 * the symbol name (because we may have changed it) and to update the offset within the relocation, because
			 * the base offset of the symbol (within its new parent section) may have changed too */
			if (oldreloc.getSymbol().isSectionName()) {
				/* get our remote section, this is our "key" to find the associated container and NEW symbol name */
				Section   sect               = oldreloc.getRemoteSection();

				/* we're getting our symbol name and container that we will use to adjust the offset with */
				Map.Entry         sectloc    = findContainer(sect);
				String            remotesymb = (String)sectloc.getKey();
				SectionContainer  remotecont = (SectionContainer)sectloc.getValue();

				/* update the symbol name in our thing... thanks! */
				newreloc.setSymbolName(remotesymb);

				/* let's update our offset to point to the right value, which accounts for the base (where our data lives in
				 * the merged section */
				long offset = remotecont.getBase(sect) + oldreloc.getOffsetAsLong();

				/* now we need to PATCH this new offset... fun, right? Thankfully, we've updated the virtaddress of the new
				 * relocation already. */
				CrystalUtils.putDWORD(section.getRawData(), (int)newreloc.getVirtualAddress(), (int)offset);
			}
			/* If we're not referring to an existing symbol... we need to create one... BTW, we're not doing gymnastics to adjust
			 * the "value" here... because ALL of the section-specific symbols were already created and mapped. This symbol likely
			 * has no section associated with it. */
			else if (newreloc.getSymbol() == null) {
				/* and... to test that assertion... */
				if (!oldreloc.getSymbol().isUndefinedSection())
					throw new RuntimeException("Relocation [[" + oldreloc + "]] refers to symbol [[" + oldreloc.getSymbol() + "]] with section [[" + oldreloc.getRemoteSection() + "]]");

				Symbol newsymb = new Symbol(object, null, oldreloc.getSymbol());
				object.getSymbols().put(newsymb.getName(), newsymb);
			}

			/* store the relocation, please and thank you */
			section.getRelocations().add(newreloc);
		}
	}

	protected void createSymbols(String sectname) {
		SectionContainer container = getContainer(sectname);
		Section          section   = object.getSection(sectname);

		Iterator i = container.getSymbols().iterator();
		while (i.hasNext()) {
			/* create a new symbol that's largely a clone of our old symbol, with the right section/object */
			Symbol oldsymbol = (Symbol)i.next();
			Symbol newsymbol = new Symbol(object, section, oldsymbol);

			/* now let's update the symbol value to reflect it's place in the merged section */
			long   value     = container.getBase(oldsymbol) + oldsymbol.getValue();
			newsymbol.setValue(value);

			/* store the symbol... please... in our.. new object! */
			object.getSymbols().put(newsymbol.getName(), newsymbol);
		}
	}

	protected void createSection(String name, List entries) {
		SectionContainer container = getContainer(name);

		/* put all of our "to merge" sections into a container */
		Iterator i = entries.iterator();
		while (i.hasNext()) {
			Section sect = (Section)i.next();
			container.add(sect, false);
		}

		/* now... let's create a section */
		Section s = new Section(object, name);

		/* let's create our merged rawdata from our container! */
		byte[] rawdata = container.getRawData();
		s.setData(rawdata);

		/* Now, let's register our new section with our result object */
		object.getSections().put(name, s);

		/* This is a good time to create a symbol for our section too */
		Symbol sectsym = Symbol.createSectionSymbol(object, s, name);
		object.getSymbols().put(name, sectsym);
	}

	public COFFMerge finish() {
		/* We're going to do some symbol magic in each of our section groups */
		objects.finish();

		/* 1. Let's create each of our sections AND add them to our result object. We need to do this
		 *    first and separate because we need the sections referenceable later when we start handling
		 *    relocations */
		Iterator i = objects.getGroups().entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry    = (Map.Entry)i.next();
			String    sectname = (String)entry.getKey();
			List      sections = (List)entry.getValue();
			createSection(sectname, sections);
		}

		/* 2. Let's create the symbol map next. */
		Iterator j = containers.keySet().iterator();
		while (j.hasNext()) {
			String    sectname = (String)j.next();
			createSymbols(sectname);
		}

		/* 3. And... lucky us... let's do the damned relocations now */
		Iterator k = containers.keySet().iterator();
		while (k.hasNext()) {
			String    sectname = (String)k.next();
			createRelocations(sectname);
		}

		/* 4. We are going to now resolve symbols that live within the SAME section container */
		Iterator l = containers.keySet().iterator();
		while (l.hasNext()) {
			String    sectname = (String)l.next();
			resolveRelocations(sectname);
		}

		return this;
	}

	public COFFObject getObject() {
		return object;
	}

	public void print() {
		System.out.println(getObject().toString());
	}
}
