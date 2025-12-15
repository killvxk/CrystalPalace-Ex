package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;
import java.nio.*;

/*
 * PICOs are similar to Cobalt Strike BOFs (but without the API). They are a way to pair COFFs with a tiny loader.
 */
public class BuildPICO {
	protected ProgramPICO  object;
	protected ExportObject eobject;
	protected byte[]       code;
	protected byte[]       data;
	protected byte[]       program;
	protected byte[]       header;

	public BuildPICO(ExportObject eobject, ProgramPICO object, byte[] code, byte[] data) {
		this.eobject = eobject;
		this.object  = object;
		this.code    = code;
		this.data    = data;
	}

	public int getEntryOffset() {
		Symbol entry = object.getCode().getSymbol(eobject.getEntrySymbolName());
		if (entry == null)
			return -1;

		return object.getCode().getBase(entry.getSection()) + (int)entry.getValue();
	}

	public int getOffset(String symb) {
		Symbol entry = object.getCode().getSymbol(symb);
		if (entry == null)
			throw new RuntimeException("Symbol '" + symb + "' not found.");

		return object.getCode().getBase(entry.getSection()) + (int)entry.getValue();
	}

	/*
	 * Generate our header content, compatbile with our PICO runner code.
	 *
	 * typedef struct {
	 * 	int codeLength;
	 * 	int dataLength;
	 * 	int rsrcOffset;
	 *	int entryAddress;
	 * } PICO_HDR;
	 *
 	 */
	public byte[] getHeader() {
		Packer temp = new Packer();
		temp.little();

		temp.addInt(object.getCode().length());
		temp.addInt(object.getData().length());
		temp.addInt(program.length + 16);	/* rsrcOffset */
		temp.addInt(getEntryOffset());		/* TODO: entryAddress */

		int enter = getEntryOffset();

		Logger.println("PICO Header");
		Logger.println("  Code: " + object.getCode().length() + " (real: " + code.length + ")");
		Logger.println("  Data: " + object.getData().length() + " (real: " + data.length + ")");
		Logger.println("Offset: " + (program.length + 16));
		Logger.println("Entry:  " + enter + " " + CrystalUtils.toHex(enter));

		return temp.getBytes();
	}

	/* these are the directives that tell the loader how to load our PICO */
	public byte[] getLoaderProgram() {
		FormatPICO program = new FormatPICO();

		/* add our copy instruction to the program, first and foremost */
		int codeRealLen  = code.length;
		int codeVirtLen  = object.getCode().length();
		int dataRealLen  = data.length;
		int dataVirtLen  = object.getData().length();

			/* copy our .text section into the first pages */
		program.CopyCode(0, 0, codeRealLen);

			/* copy all of our data into the following poages */
		program.CopyData(codeRealLen, 0, dataRealLen);

		Logger.println("CODE is at: 0 to " + codeRealLen);
		Logger.println("DATA is at: " + codeVirtLen + " (real: " + codeRealLen + ") + " + dataVirtLen + " (real: " + dataRealLen + ")");

		/* put in our base patchups */
		Iterator i = object.getPatchUps().iterator();
		while (i.hasNext()) {
			ProgramPICO.PatchUp p = (ProgramPICO.PatchUp)i.next();
			Relocation          r = p.getRelocation();

			if (r.is_x64_rel32()) {
				/*
				 * I'm doing these checks here in case I get the bright idea that "oh hey, I can make jump tables work
				 * by patching diffs in .rdata" and suddenly it's silently failing... it's because the loading directive
				 * is only tailored to a specific situation. Also, I tried to get x64 jump tables working and gave up, so I'm
				 * punting on that for now... but this is defensive code for later, should I try to ressurect that work.
				 */
				if (p.isSourceCode() && p.isTargetCode()) {
					throw new RuntimeException("Can't generate diff loader directive for code->code relocation " + r);
				}
				else if (p.isSourceCode() && p.isTargetData()) {
					program.PatchBaseDiff((int)p.getVirtualAddress());
				}
				else if (p.isSourceData() && p.isTargetCode()) {
					throw new RuntimeException("Can't generate diff loader directive for data->code relocation " + r);
				}
				else if (p.isSourceData() && p.isTargetData()) {
					throw new RuntimeException("Can't generate diff loader directive for data->data relocation " + r);
				}
			}
			else if (r.is("x64", Relocation.IMAGE_REL_AMD64_ADDR64) || r.is("x86", Relocation.IMAGE_REL_I386_DIR32)) {
				if (p.isSourceCode() && p.isTargetCode()) {
					program.PatchTextText((int)p.getVirtualAddress());
				}
				else if (p.isSourceCode() && p.isTargetData()) {
					program.PatchTextBase((int)p.getVirtualAddress());
				}
				else if (p.isSourceData() && p.isTargetCode()) {
					program.PatchBaseText((int)p.getVirtualAddress());
				}
				else if (p.isSourceData() && p.isTargetData()) {
					program.PatchBaseBase((int)p.getVirtualAddress());
				}
			}
			else {
				throw new RuntimeException("I can't generate a loader directive for relocation: " + r);
			}
		}

		/* next, handle our import table */
		Iterator j = object.getImports().entrySet().iterator();
		while (j.hasNext()) {
			Map.Entry entry  = (Map.Entry)j.next();
			String    module = (String)entry.getKey();
			Map       funcs  = (Map)entry.getValue();

			program.LoadLibrary(module);

			Iterator k = funcs.entrySet().iterator();
			while (k.hasNext()) {
				Map.Entry fentry = (Map.Entry)k.next();
				String    func   = (String)fentry.getKey();
				Section   sect   = (Section)fentry.getValue();

				program.GetProcAddress(func);
				program.PatchFunction(object.getData().getBase(sect));
			}
		}

		/* let's handle our (internal) API calls */
		Iterator m = object.getLocalImports().entrySet().iterator();
		while (m.hasNext()) {
			Map.Entry entry  = (Map.Entry)m.next();
			String    func   = (String)entry.getKey();
			Section   sect   = (Section)entry.getValue();
			int       index  = eobject.getAPI(func);

			if (index == -1)
				throw new RuntimeException("Function " + func + " is not imported and not in MODULE$function format");

			program.PatchImport(object.getData().getBase(sect), index);
		}

		/* let's handle our exports... all we're doing is mapping tags to offsets in .text. That's it */
		Iterator n = eobject.getExports().iterator();
		while (n.hasNext()) {
			Map.Entry entry = (Map.Entry)n.next();
			String    func  = (String)entry.getKey();
			int       tag   = (int)entry.getValue();

			program.Export(tag, getOffset(func));
		}

		/* ... */
		program.Complete();

		return program.getBytes();
	}

	public byte[] export() {
		program = getLoaderProgram();
		header  = getHeader();

		Concat c = new Concat();
		c.add(header);
		c.add(program);
		c.add(code);
		c.add(data);

		Logger.print_stat("export() header " + header.length + "b, program " + program.length + "b, content " + (code.length + data.length) + "b");

		return c.get();
	}
}
