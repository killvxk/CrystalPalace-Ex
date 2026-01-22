package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import crystalpalace.btf.*;
import crystalpalace.merge.*;

import crystalpalace.spec.*;

import java.util.*;
import java.nio.*;
import java.io.*;

/*
 * This is a container to keep track of our exported object state and carry out various actions on it.
 */
public class ExportObject {
	protected COFFObject  object;
	protected Set         btfoptions;
	protected String      type;
	protected Map         links      = new LinkedHashMap();
	protected List        apitable   = new LinkedList();
	protected PrintStream disasm_out = null;
	protected PrintStream coffpr_out = null;

	protected Map         patches    = new LinkedHashMap();

	protected DFR         resolvers  = null;
	protected String      x86retaddr = null;
	protected String      getbss     = null;
	protected Hooks       hooks      = null;
	protected Exports     exports    = null;

	public ExportObject(SpecProgram program, COFFObject object, String type, Set btfoptions) {
		this.type       = type;
		this.btfoptions = btfoptions;
		this.object     = object;
		this.resolvers  = new DFR(this);
		this.hooks      = new Hooks(this);

		this.exports    = new Exports(this, program.getTags());

		setAPI("LoadLibraryA, GetProcAddress");
	}

	public boolean x64() {
		return object.getMachine().equals("x64");
	}

	public String getEntrySymbolName() {
		Symbol entry = object.findEntrySymbol();
		if (entry != null)
			return entry.getName();

		/* fallback to default names if not found */
		String machine = object.getMachine();
		if ("x64".equals(machine))
			return "go";
		else if ("x86".equals(machine))
			return "_go";

		throw new RuntimeException(machine + " machine is not supported");
	}

	/*
	 * We want to export() our code (aka, merge it) and disassemble it at this point in time, I want any errors
	 * that occur during this process to happen here. A way forward might be to use the "coff" output type and
	 * feed that merged output to our disassembler. I'm largely OK with this.
	 */
	public void disassemble(File out) throws FileNotFoundException {
		if (disasm_out != null)
			throw new RuntimeException("disassemble is already defined");

		disasm_out = new PrintStream(out);
	}

	public void coffparse(File out) throws FileNotFoundException {
		if (coffpr_out != null)
			throw new RuntimeException("coffparse is already defined");

		coffpr_out = new PrintStream(out);
	}

	/*
	 * I think we want to make data available to be appended to or placed into an object or something. ?!?
	 */
	public void link(String name, byte[] value) {
		links.put(name, new SectionData(name, value));
	}

	public void linkFunction(String name, byte[] value) {
		check(name);

		if (object.getSymbol(name).getSection() != null)
			throw new RuntimeException("Symbol " + name + " is already defined.");

		links.put(name, new SectionCode(name, value));
	}

	/* patch the contents of our symbol... somewhere */
	public void patch(String symb, byte[] data) {
		object.checkPatch(symb, data);
		patches.put(symb, data);
	}

	public Exports getExports() {
		return exports;
	}

	/* let's apply this... directly to... the fucking COFF */
	public void merge(COFFObject object2) {
		List temp = new LinkedList();
		temp.add(object2);

		merge(temp);
	}

	/* multi-merge! */
	public void merge(List objects) {
		COFFMerge merge = new COFFMerge();
		merge.merge(object);

		Iterator i = objects.iterator();
		while (i.hasNext()) {
			COFFObject object2 = (COFFObject)i.next();
			merge.merge(object2);
		}

		object = merge.finish().getObject();
	}

	public void remap(String oldsymb, String newsymb) {
		object.remapSymbol(oldsymb, newsymb);
	}

	public DFR getResolvers() {
		return resolvers;
	}

	public Hooks getHooks() {
		return hooks;
	}

	public boolean hasX86Retaddr() {
		return x86retaddr != null;
	}

	protected void check(String symbol) {
		Symbol temp = (Symbol)object.getSymbol(symbol);

		/* Crystal Palace largely expects the user to specify symbols and not functions. x64 they're the same. x86, it's often
		 * an underscore. But, with attach and __stdcall hooks, we have this fucking @ convention. I *could* normalize in some
		 * places... but that makes the .spec language less consistent. My fear with this inconsistency is that some language
		 * features (e.g., redirect, when the target doesn't exist) can't differentiate between a out-of-convention function or
		 * a symbol that doesn't exist--so making it clear that symbols are always expected is important. My in-between idea is
		 * to give really good suggestions about what symbol the provided function might map to. */
		if ( temp == null && "x86".equals(object.getMachine()) ) {
			Iterator i = object.getSymbols().keySet().iterator();
			while (i.hasNext()) {
				String next  = (String)i.next();
				String cand1 = "_" + symbol + "@";
				String cand2 = symbol + "@";

				if (next.startsWith(cand2) || next.startsWith(cand1))
					throw new RuntimeException("Symbol " + symbol + " does not exist. Did you mean " + next + "?");
			}

			if (object.getSymbol("_" + symbol) != null)
				throw new RuntimeException("Symbol " + symbol + " does not exist. Did you mean _" + symbol + "?");
		}

		if (temp == null)
			throw new RuntimeException("Symbol " + symbol + " does not exist.");

		if (!temp.isFunction())
			throw new RuntimeException("Symbol " + symbol + " is not a function.");
	}

	public void fixX86References(String retaddr) {
		x86retaddr = retaddr;
		check(retaddr);
	}

	public void fixBSSReferences(String _getbss) {
		getbss = _getbss;
		check(getbss);
	}

	/*
	 * So, we're going to have a PICO concept of "APIs". An API is a function
	 * that is not decorated (e.g., not LIB$ attached to it). And, the PICO loader
	 * will receive this library as an extension of IMPORTFUNCS. We are going to treat
	 * that first parameter as basically an array of pointers, because that's EXACTLY
	 * what it is. ANYWAYS... if something is expected to be in that array of pointers
	 * we are going to... very nicely... y'know... index into that to handle the linking
	 * of that pointer into our GOT for function pointers. A nice clean way of setting
	 * up a contract for resolving internal APIs, making it expandable, and most importantly
	 * NOT PACKAGING strings that give away too easily what we're resolving.
	 */
	public int getAPI(String _symbol) {
		Iterator i = apitable.iterator();
		for (int x = 0; i.hasNext(); x++) {
			String sym = (String)i.next();
			if (sym.equals(_symbol))
				return x;
		}

		return -1;
	}

	/* set the API for this (presumed) PICO */
	public void setAPI(String symbols) {
		/* initialize this */
		apitable = new LinkedList();

		/* add our values to it */
		String[] blah = symbols.split(",\\s*");
		for (int x = 0; x < blah.length; x++) {
			apitable.add(blah[x].trim());
		}

		/* and then do some sanity checks */
		if (getAPI("LoadLibraryA") != 0)
			throw new RuntimeException("LoadLibraryA is required as the first API entry.");

		if (getAPI("GetProcAddress") != 1)
			throw new RuntimeException("GetProcAddress is required as the second API entry.");
	}

	/*
	 * Make our error checking robust, are we exporting PIC or not?
	 */
	public boolean isPIC() {
		return "pic".equals(type) || "pic64".equals(type);
	}

	public boolean isPICO() {
		return "object".equals(type);
	}

	public boolean isCOFF() {
		return "coff".equals(type);
	}

	public String getMachine() {
		return object.getMachine();
	}

	public Section getLinkedSection(String name) {
		return (Section)links.get(name);
	}

	public Section getSection(String name) {
		return object.getSection(name);
	}

	public List getLinks() {
		return new LinkedList( links.values() );
	}

	public List getExecutableLinks(boolean eXecutable) {
		List results = getLinks();
		Iterator i = results.iterator();
		while (i.hasNext()) {
			Section sect = (Section)i.next();
			if (sect.isExecutable() != eXecutable)
				i.remove();
		}

		return results;
	}

	/* Again, API to aid our error checking */
	public String getType() {
		return type;
	}

	public boolean is(String _type) {
		return type.equals(_type);
	}

	/*
	 * Yeap, act on our directives (type) and export an array of bytes... easy peasy.
	 */
	public byte[] export() {
		/* normalize and run out BTF on our file, thanks! */
		COFFMerge merge = new COFFMerge();
		merge.merge(object);
		object = merge.finish().getObject();

		/* BTF pass 0 */
		object = new Modify(object).applyHooks(exports, hooks);

		/* BTF pass 1 */
		object = new Modify(object).fixPIC(resolvers, x86retaddr, getbss);

		/* BTF pass 2 */
		object = new Modify(object).mutate(isPIC(), exports, btfoptions);

		/* print out our parsed COFF, if the .spec requested it */
		if (coffpr_out != null) {
			coffpr_out.println(object.toString());
			coffpr_out.flush();
			coffpr_out.close();
		}

		/* print out our disassembled program, if the .spec requested it */
		if (disasm_out != null) {
			CodeUtils.print(disasm_out, Code.Init(object).analyze());
			disasm_out.flush();
			disasm_out.close();
		}

		/* apply our patches */
		Iterator i = patches.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry)i.next();
			object.patch((String)entry.getKey(), (byte[])entry.getValue());
		}

		/* And... the magic happens here! */
		if (is("pic"))
			return new ProgramPIC64(this).export();
		else if (is("pic64"))				/* deprecated functionality as it's now the same thing as "make pic" */
			return new ProgramPIC64(this).export();
		else if (is("object"))
			return new ProgramPICO(this).export();
		else if (is("coff"))
			return new ProgramCOFF(this).export();

		return new byte[0];
	}
}
