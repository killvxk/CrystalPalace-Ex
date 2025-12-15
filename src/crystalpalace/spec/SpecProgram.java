package crystalpalace.spec;

import crystalpalace.coff.*;
import crystalpalace.pe.*;
import crystalpalace.util.*;
import crystalpalace.export.*;

import java.util.*;
import java.util.zip.*;
import java.io.*;

public class SpecProgram {
	protected Map        directives = new HashMap();
	protected Stack      state      = new Stack();

	protected String     parent     = ".";
	protected String     name       = "";

	/* state information to aid error reporting */
	protected String     last       = "";
	protected Map        cmdvars    = null;
	protected SpecObject larg       = null;
	protected String     ltarg      = null;
	protected String     ltarch     = null;

	/* a program-global map of PICO exported functions to integer tags */
	protected TagStore   tags       = new TagStore();

	/* program-specific local vars, does NOT propagate to run */
	protected Map        locals     = new HashMap();

	/* the logger for this program */
	protected List       loggers    = new LinkedList();

	/* set the logger for this program */
	public void addLogger(SpecLogger logger) {
		loggers.add(logger);
	}

	/* remove the logger */
	public void removeLogger(SpecLogger logger) {
		loggers.remove(logger);
	}

	/* log our message */
	public void logSpecMessage(SpecMessage message) {
		Iterator i = loggers.iterator();
		while (i.hasNext()) {
			((SpecLogger)i.next()).logSpecMessage(message);
		}
	}

	/* reset the state of this program, in case its used a second time */
	public void reset() {
		state = new Stack();
		last  = "";
		larg  = null;
		ltarg = null;
	}

	public TagStore getTags() {
		return tags;
	}

	public SpecProgram(String parent) {
		this.parent = parent;

		/* make the default name of the spec, the filename only */
		name = new java.io.File(parent).getName();
	}

	public String getFile() {
		return parent;
	}

	public String getLastTarget() {
		return ltarg;
	}

	public SpecObject getLastArgument() {
		return larg;
	}

	public String getLastCommand() {
		return last;
	}

	public Stack getStack() {
		return state;
	}

	public boolean targets(String arch) {
		return getInstructionsForLabel(arch).size() > 0;
	}

	public List getInstructionsForLabel(String label) {
		if (!directives.containsKey(label))
			directives.put(label, new LinkedList());

		return (LinkedList)directives.get(label);
	}

	protected void push(byte[] b, String source) {
		state.push( new SpecObject(this, b, source) );
		larg = null;
	}

	protected void push(ExportObject o, String source) {
		state.push( new SpecObject(this, o, source) );
		larg = null;
	}

	protected void push(SpecObject obj) {
		state.push(obj);
		larg = null;
	}

	public Map getLastVars() {
		if (cmdvars == null)
			return new HashMap();
		return cmdvars;
	}

	protected SpecObject pop() throws SpecProgramException {
		if (state.empty())
			throw new SpecProgramException(this, "POP - stack is empty");

		larg = (SpecObject)state.pop();

		return larg;
	}

	public String getCallTarget(String name, String label, String arch) throws SpecProgramException {
		if ("".equals(name)) {
			if (targets(label))
				return label;
			else if (targets(arch))
				return arch;
			else
				throw new SpecProgramException(this, "Spec does not have target for '" + label + "' or '" + arch + "'");
		}
		else {
			if (targets(name + "." + label))
				return name + "." + label;
			else if (targets(name + "." + arch))
				return name + "." + arch;
			else
				throw new SpecProgramException(this, "Spec does not have target for '" + name + "." + label + "' or '" + name + "." + arch + "'");
		}
	}

	public void _run(String name, String label, String arch, Map env) throws SpecProgramException, SpecParseException {
		String calltarget = getCallTarget(name, label, arch);

		/* get instructions first based on our label, fall back to our arch otherwise */
		List inst = getInstructionsForLabel(calltarget);

		/* set this, please */
		ltarg  = calltarget;
		ltarch = arch;

		Iterator i = inst.iterator();
		while (i.hasNext()) {
			CommandParser command = (CommandParser)i.next();

			/* let this happen first, so if our var resolution fails, we know why/where */
			last = command.getOriginal();

			/* run the command */
			_runCommand(i, label, arch, env, command);
		}
	}

	protected void _runCommand(Iterator i, String label, String arch, Map env, CommandParser command) throws SpecProgramException, SpecParseException {
		String        args[];

		/* get our arguments, but evaluate the %vars too */
		try {
			cmdvars = null;
			CommandParser.Arguments temp = command.getArguments(new SpecVars(env, locals));
			args    = temp.args;
			cmdvars = temp.vars;
		}
		catch (RuntimeException rex) {
			throw new SpecProgramException(this, rex.getMessage());
		}

		// load [file]          BYTES    -		Read file and push content onto stack
		if ("load".equals(command.getCommand()) && args.length == 1) {
			try {
				File temp = getFileFromArg(args[0]);
				push( CrystalUtils.readFromFile( temp.getPath() ), temp.getName() );
			}
			catch (IOException ioex) {
				throw new SpecProgramException(this, ioex.getMessage());
			}
		}
		else if ("load".equals(command.getCommand()) && args.length == 2) {
			try {
				File temp = getFileFromArg(args[1]);
				putEnv(env, args[0], CrystalUtils.readFromFile(temp.getPath()));
			}
			catch (IOException ioex) {
				throw new SpecProgramException(this, ioex.getMessage());
			}
		}
		else if ("mergelib".equals(command.getCommand())) {
			try {
				/* read our .zip file and build a list of COFFs */
				File        temp  = getFileFromArg(args[0]);
				ZipFile     zip   = new ZipFile(temp);
				Enumeration e     = zip.entries();
				List        coffs = new LinkedList();
				while (e.hasMoreElements()) {
					ZipEntry    entry = (ZipEntry)e.nextElement();
					InputStream is    = zip.getInputStream(entry);
					byte[]      data  = CrystalUtils.readBytes(is, (int)entry.getSize());

					push( data, entry.getName() );
					SpecObject  arg   = pop();
					coffs.add(createCOFF(arg));
				}

				/* do the merge */
				SpecObject   obj   = pop();
				obj.getObject().merge(coffs);
				push(obj);
			}
			catch (RuntimeException rtx) {
				throw new SpecProgramException(this, rtx.getMessage());
			}
			catch (IOException ioex) {
				throw new SpecProgramException(this, ioex.getMessage());
			}
		}
		/* local function call */
		else if (command.getCommand().startsWith(".")) {
			/* backup our local variables */
			Map oldlocals = locals;
			locals = new HashMap();

			/* backup our ltarg */
			String oldltarg = ltarg;

			/* add the user-defined locals */
			for (int x = 0; x < args.length; x++) {
				locals.put("%" + (x + 1), args[x]);
			}

			/* run the .spec */
			_run(command.getCommand().substring(1), label, arch, env);

			/* restore our ltarg */
			ltarg = oldltarg;

			/* restore our local variables */
			locals = oldlocals;
		}
		/* run default target in remote script */
		else if ("run".equals(command.getCommand())) {
			try {
				File temp = getFileFromArg(args[0]);

				/* create a NEW linkspec */
				LinkSpec spec    = LinkSpec.Parse(temp.getPath());

				/* push our stack over to the other program. */
				spec.program.state = state;

				/* push our tags over to the other program. */
				spec.program.tags = tags;

				/* push our loggers over to the other program. */
				spec.program.loggers = loggers;

				/* add the user-defined locals */
				for (int x = 1; x < args.length; x++) {
					spec.program.locals.put("%" + x, args[x]);
				}

				/* run the .spec */
				spec.program._run("", label, arch, env);
			}
			catch (IOException ioex) {
				throw new SpecProgramException(this, ioex.getMessage());
			}
		}
		/* remote function call */
		else if ("call".equals(command.getCommand())) {
			try {
				File temp = getFileFromArg(args[0]);

				/* create a NEW linkspec */
				LinkSpec spec    = LinkSpec.Parse(temp.getPath());

				/* push our stack over to the other program. */
				spec.program.state = state;

				/* push our tags over to the other program. */
				spec.program.tags = tags;

				/* push our loggers over to the other program. */
				spec.program.loggers = loggers;

				/* add the user-defined locals */
				for (int x = 2; x < args.length; x++) {
					spec.program.locals.put("%" + (x - 1), args[x]);
				}

				/* run the .spec */
				spec.program._run(args[1], label, arch, env);
			}
			catch (IOException ioex) {
				throw new SpecProgramException(this, ioex.getMessage());
			}
		}
		else if ("setg".equals(command.getCommand())) {
			env.put(args[0], args[1]);
		}
		else if ("set".equals(command.getCommand())) {
			locals.put(args[0], args[1]);
		}
		else if ("pack".equals(command.getCommand()) ) {
			String var      = args[0];
			String template = args[1];

			try {
				SpecPack pack = new SpecPack(this, template);
				putEnv(env, var, pack.apply(args, 2, env));
			}
			catch (NumberFormatException nex) {
				throw new SpecProgramException(this, nex.getClass().getSimpleName() + " " + nex.getMessage());
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}
		}
		else if ("resolve".equals(command.getCommand())) {
			/* get the original value */
			String val = new SpecVars(env, locals).resolve(args[0]);

			/* walk all of our values, canonicalize them, add them to a linked list */
			List   vals = new LinkedList();
			Iterator ii = CrystalUtils.toList(val).iterator();
			while (ii.hasNext()) {
				String partial = (String)ii.next();
				File   temp    = getFileFromArg(partial);

				try {
					vals.add(temp.getCanonicalPath());
				}
				catch (IOException ioex) {
					throw new SpecProgramException(this, ioex.getMessage() + ": " + temp.getPath());
				}
			}

			/* put the value into the right spot in our vars */
			if (locals.containsKey(args[0]))
				locals.put(args[0], String.join(", ", vals));
			else if (env.containsKey(args[0]))
				env.put(args[0], String.join(", ", vals));
		}
		else if ("coffparse".equals(command.getCommand())) {
			SpecObject obj = pop();

			try {
				File temp = getOutFileFromArg(args[0]);
				obj.getObject().coffparse(temp);
			}
			catch (IOException ioex) {
				throw new SpecProgramException(this, ioex.getMessage());
			}

			push(obj);
		}
		else if ("disassemble".equals(command.getCommand())) {
			SpecObject obj = pop();

			try {
				File temp = getOutFileFromArg(args[0]);
				obj.getObject().disassemble(temp);
			}
			catch (IOException ioex) {
				throw new SpecProgramException(this, ioex.getMessage());
			}

			push(obj);
		}
		// link "section"       OBJECT  OBJECT, BYTES	Link BYTES to section in OBJECT
		else if ("link".equals(command.getCommand())) {
			byte[]       value = pop().getBytes();
			SpecObject   obj   = pop();

			try {
				obj.getObject().link(args[0], value);
			}
			catch (RuntimeException rex) {
				handleException(rex);
			}

			push(obj);
		}
		else if ("linkfunc".equals(command.getCommand())) {
			byte[]       value = pop().getBytes();
			SpecObject   obj   = pop();

			if (obj.getObject().isCOFF())
				throw new RuntimeException("linkfunc is not supported with COFF exports");

			try {
				obj.getObject().linkFunction(args[0], value);
			}
			catch (RuntimeException rex) {
				handleException(rex);
			}

			push(obj);
		}
		// export               -	OBJECT     	export object, return
		else if ("export".equals(command.getCommand())) {
			SpecObject   obj  = pop();
			ExportObject eobj = obj.getObject();
			try {
				push( eobj.export(), obj.getSource() );
			}
			catch (RuntimeException rex) {
				handleException(rex);
			}
		}
		// make object		OBJECT  BYTES		Turn FILE into ready-to-export PICO object
		// make pic 	        OBJECT  BYTES		Turn FILE into ready-to-export PIC object
		// make pic 	        OBJECT  BYTES		Turn FILE into ready-to-export PIC64 object
		else if ( "make".equals(command.getCommand()) ) {
			if ( !"x64".equals(arch) && "make pic64".equals(command.getFullCommand()) )
				throw new SpecProgramException(this, "make pic64 is x64-only");

			SpecObject obj = pop();

			try {
				ExportObject exp = new ExportObject(this, createCOFF(obj), args[0], command.getOptions());
				push( exp, obj.getSource() );
			}
			catch (RuntimeException rex) {
				handleException(rex);
			}
		}
		else if ( "merge".equals(command.getCommand()) ) {

			try {
				SpecObject   arg   = pop();
				COFFObject   coff  = createCOFF(arg);

				SpecObject   obj   = pop();
				obj.getObject().merge(coff);
				push(obj);
			}
			catch (RuntimeException rex) {
				handleException(rex);
			}
		}
		else if ("reladdr".equals(command.getCommand())) {
			throw new SpecProgramException(this, "reladdr is removed. Use fixptrs to avoid the x86 address hacks");
		}
		else if ("fixptrs".equals(command.getCommand())) {
			SpecObject   obj    = pop();
			String       symbol = args[0];

			if ( !"x86".equals(arch) || !obj.getObject().isPIC() )
				throw new SpecProgramException(this, "fixptrs [_symbol] is x86 PIC-only");

			try {
				obj.getObject().fixX86References(symbol);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);
		}
		else if ("fixbss".equals(command.getCommand())) {
			SpecObject   obj    = pop();
			String       symbol = args[0];

			if (!obj.getObject().isPIC() )
				throw new SpecProgramException(this, "fixbss [symbol] is PIC-only");

			try {
				obj.getObject().fixBSSReferences(symbol);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);
		}
		else if ("dfr".equals(command.getCommand())) {
			SpecObject   obj    = pop();
			String       symbol = args[0];

			if (!obj.getObject().isPIC())
				throw new SpecProgramException(this, "dfr [symbol] [method] <modules> is PIC-only");

			try {
				if (args.length == 3) {
					obj.getObject().getResolvers().addResolver(symbol, args[1], args[2]);
				}
				else {
					obj.getObject().getResolvers().setDefaultResolver(symbol, args[1]);
				}
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);
		}
		else if ( "attach".equals(command.getCommand()) ) {
			SpecObject   obj     = pop();

			try {
				obj.getObject().getHooks().attach(args[0], args[1]);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);

		}
		else if ( "redirect".equals(command.getCommand()) ) {
			SpecObject   obj     = pop();

			try {
				obj.getObject().getHooks().redirect(args[0], args[1]);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);

		}
		else if ( "addhook".equals(command.getCommand()) ) {
			SpecObject   obj     = pop();

			try {
				if (args.length == 1)
					obj.getObject().getHooks().addResolveHook(args[0]);
				else
					obj.getObject().getHooks().addResolveHook(args[0], args[1]);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);

		}
		else if ( "filterhooks".equals(command.getCommand()) ) {
			SpecObject   obj     = pop();

			try {
				obj.getObject().getHooks().filterResolveHooks(getFromEnv(env, args[0]));
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);

		}
		else if ( "preserve".equals(command.getCommand()) ) {
			SpecObject   obj     = pop();

			try {
				obj.getObject().getHooks().preserve(args[0], args[1]);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);

		}
		else if ( "protect".equals(command.getCommand()) ) {
			SpecObject   obj     = pop();

			try {
				obj.getObject().getHooks().protect(args[0]);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);
		}
		else if ( "optout".equals(command.getCommand()) ) {
			SpecObject   obj     = pop();

			try {
				obj.getObject().getHooks().optout(args[0], args[1]);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);
		}
		else if ( "remap".equals(command.getCommand()) ) {
			SpecObject obj = pop();

			try {
				obj.getObject().remap(args[0], args[1]);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);
		}
		else if ("exportfunc".equals(command.getCommand())) {
			SpecObject obj = pop();

			if (!obj.getObject().isPICO())
				throw new SpecProgramException(this, "exportfunc is for PICOs only");

			try {
				obj.getObject().getExports().export(args[0], args[1]);
			}
			catch (RuntimeException rex) {
				throw new SpecProgramException(this, rex.getMessage());
			}

			push(obj);
		}
		/* generate a random string and stick it into an environment var */
		else if ("generate".equals(command.getCommand())) {
			String var  = args[0];
			int    len  = CrystalUtils.parseInt(args[1], 0);
			if (len < 0)
				throw new SpecProgramException(this, "Nice try, we can't generate a negative size byte array");

			byte[] val  = new byte[len];

			try {
				java.security.SecureRandom rng = java.security.SecureRandom.getInstanceStrong();
				rng.nextBytes(val);
			}
			catch (Exception ex) {
				throw new SpecProgramException(this, ex.getMessage());
			}

			putEnv(env, var, val);
		}
		/* patch a symbol inside of one of our COFF's sections */
		else if ("patch".equals(command.getCommand())) {
			String var = args[1];
			String sym = args[0];

			SpecObject obj  = pop();

			if ("x86".equals(arch) && obj.getObject().isPIC() && !obj.getObject().hasX86Retaddr())
				throw new SpecProgramException(this, "x86 PIC requires fixptrs is set to use patch");

			try {
				obj.getObject().patch(sym, getFromEnv(env, var));
			}
			catch (RuntimeException rex) {
				handleException(rex);
			}

			push(obj);
		}
		else if ("import".equals(command.getCommand())) {
			String     funcs = args[0];
			SpecObject obj   = pop();

			if ("object".equals( obj.getObject().getType() )) {
				try {
					obj.getObject().setAPI(funcs);
				}
				catch (RuntimeException rex) {
					handleException(rex);
				}
			}
			else {
				throw new SpecProgramException(this, "Argument is not a PICO (COFF) - can't import functions to it");
			}

			push(obj);
		}
		else if ("xor".equals(command.getCommand())) {
			SpecObject obj = pop();

			byte[] key = getFromEnv(env, args[0]);
			byte[] val = obj.getBytes();

			for (int x = 0; x < val.length; x++) {
				val[x] ^= key[x % key.length];
			}

			push( val, obj.getSource() );
		}
		else if ("rc4".equals(command.getCommand())) {
			SpecObject obj = pop();

			byte[] key = getFromEnv(env, args[0]);
			byte[] val = obj.getBytes();

			try {
				val = CrystalUtils.rc4encrypt(key, val);
			}
			catch (Exception ex) {
				throw new SpecProgramException(this, ex.getMessage());
			}

			push( val, obj.getSource() );
		}
		// prepend [length] to our [data] on the stack
		else if ("preplen".equals(command.getCommand())) {
			SpecObject obj = pop();

			byte[] data = obj.getBytes();

			Packer pack = new Packer();
			pack.little();
			pack.addData((byte[])data);	/* prepends our length for us */

			push(pack.getBytes(), obj.getSource());
		}
		else if ("prepsum".equals(command.getCommand())) {
			SpecObject obj = pop();

			byte[] data = obj.getBytes();

			Packer pack = new Packer();
			pack.little();
			pack.addDataVerify((byte[])data); /* prepends adler32sum */

			push(pack.getBytes(), obj.getSource());
		}
		// push $VAR            BYTES   -               Push $VAR content onto stack
		else if ("push".equals(command.getCommand())) {
			push(getFromEnv(env, args[0]), args[0]);
		}
		else if ("echo".equals(command.getCommand())) {
			List stuff = new LinkedList();
			for (int x = 0; x < args.length; x++) {
				stuff.add(args[x]);
			}

			logSpecMessage( SpecMessage.Echo(this, String.join(" ", stuff)) );
		}
		else if ( "foreach".equals(command.getCommand()) ) {
			/* get the command we want to execute */
			CommandParser commandz = (CommandParser)i.next();

			if ( CrystalUtils.toSet("foreach, next").contains(commandz.getCommand()) )
				throw new SpecProgramException(this, "Nested foreach/next is not allowed");

			/* do our foreach loop, right? */
			Iterator j = CrystalUtils.toList(args[0]).iterator();
			while (j.hasNext()) {
				locals.put("%_", (String)j.next());

				/* run our command */
				_runCommand(i, label, arch, env, commandz);
			}

			/* get rid of %_, now that we're done */
			locals.remove("%_");
		}
		else if ( "next".equals(command.getCommand()) ) {
			/* get the command we want to execute */
			CommandParser commandz = (CommandParser)i.next();

			if ( CrystalUtils.toSet("foreach, next").contains(commandz.getCommand()) )
				throw new SpecProgramException(this, "Nested foreach/next is not allowed");

			/* grab our next value */
			String value = new SpecVars(env, locals).shift(args[0]);

			/* if that value is not null... or empty... run our command */
			if (value != null && !"".equals(value)) {
				locals.put("%_", value);
				_runCommand(i, label, arch, env, commandz);
			}

			/* get rid of %_, now that we're done */
			locals.remove("%_");
		}
		else {
			throw new SpecProgramException(this, "This is a bug? Did not process '" + command.getOriginal() + "'");
		}
	}

	public void runConfig(String label, String arch, Map env) throws SpecProgramException, SpecParseException {
		_run("", label, arch, env);

		if (tags.getTags().size() > 0)
			throw new SpecProgramException(this, "Tag store is not empty. Do not use exportfunc from a @config.spec file.");

		if (!state.empty())
			throw new SpecProgramException(this, "Stack is not empty. Make sure all objects are processed");
	}

	public byte[] run(String label, String arch, Map env) throws SpecProgramException, SpecParseException {
		_run("", label, arch, env);

		if (state.empty())
			throw new SpecProgramException(this, "Stack is empty. Where's the bytes I want to return?");

		SpecObject obj = pop();

		if (!state.empty())
			throw new SpecProgramException(this, "Stack is not empty. Make sure all objects are processed");

		return obj.getBytes();
	}

	/* Since I was too lazy to create a COFF[Verb]Exception class; I wrap all of my various COFF-related errors as
	 * unchecked RuntimeExceptions. Works great, except when I have a bonafide... oops.... I didn't mean to throw that
	 * type issue. So, we're checking here if the exception is a RuntimeException or something else that needs more
	 * context to aid fixing it. */
	public void handleException(RuntimeException rex) throws SpecProgramException {
		if (rex.getClass() != RuntimeException.class) {
			CrystalUtils.handleException(rex);
			throw new SpecProgramException(this, rex.getClass().getName() + ": " + rex.getMessage());
		}
		else {
			throw new SpecProgramException(this, rex.getMessage());
		}
	}

	public COFFObject createCOFF(SpecObject obj) throws SpecProgramException {
		COFFParser parser = new COFFParser();

		try {
			parser.parse(obj.getBytes());
		}
		catch (RuntimeException rex) {
			handleException(rex);
		}

		if (! ltarch.equals(parser.getObject().getMachine()) )
			throw new SpecProgramException(this, parser.getObject().getMachine() + " COFF arch differs from " + ltarg + " .spec target");

		return parser.getObject();
	}

	public File getFileFromArg(String arg) throws SpecProgramException {
		File temp = new File(arg).isAbsolute() ? new File(arg) : new File( new File(parent).getParentFile(), arg );
		if (!temp.exists())
			throw new SpecProgramException(this, "File does not exist " + temp.getPath());

		if (temp.isDirectory())
			throw new SpecProgramException(this, "File is a folder " + temp.getPath());

		if (!temp.canRead())
			throw new SpecProgramException(this, "File is not readable " + temp.getPath());

		return temp;
	}

	public File getOutFileFromArg(String arg) throws SpecProgramException {
		File temp = new File( ".", arg );

		if (temp.isDirectory())
			throw new SpecProgramException(this, "Out file is a folder " + temp.getPath());

		if (temp.exists() && !temp.canWrite())
			throw new SpecProgramException(this, "Out file is not writable " + temp.getPath());

		return temp;
	}

	public void putEnv(Map env, String key, byte[] data) throws SpecProgramException {
		if (!key.startsWith("$"))
			throw new SpecProgramException(this, "Invalid argument. Try: $" + key);
		else if (env.containsKey(key))
			throw new SpecProgramException(this, key + " is already present in environment. Can't overwrite");
		else
			env.put(key, data);
	}

	public byte[] getFromEnv(Map env, String key) throws SpecProgramException {
		if (!key.startsWith("$")) {
			throw new SpecProgramException(this, "Invalid argument. Try: $" + key);
		}
		else if (env.containsKey(key)) {
			Object temp = env.get(key);

			if (temp.getClass().isArray() && temp.getClass().getComponentType() == byte.class) {
				return (byte[])temp;
			}
			else {
				throw new SpecProgramException(this, "Var " + key + " is not a byte[]");
			}
		}
		else {
			throw new SpecProgramException(this, "Var " + key + " is not set");
		}
	}
}
