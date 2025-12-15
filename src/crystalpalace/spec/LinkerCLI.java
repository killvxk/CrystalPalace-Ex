package crystalpalace.spec;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import crystalpalace.export.*;

import java.util.*;
import java.io.*;

/*
 * This was getting too complicated to just keep inside of LinkerSpec, so it gets its own class now
 */
public class LinkerCLI implements SpecLogger {
	protected Map        env         = new HashMap();
	protected String     specfile    = null;
	protected Capability capability  = null;
	protected boolean    resolveNext = false;

	protected LinkerCLI(String specfile, Capability capability) {
		this.specfile   = specfile;
		this.capability = capability;
	}

	public void processInclude(String file) throws SpecParseException, SpecProgramException, IOException {
		LinkSpec spec = LinkSpec.Parse(file);
		spec.addLogger(this);
		spec.runConfig(capability, env);
	}

	public void processKey(String key, String value) {
		try {
			byte[] temp = CrystalUtils.hexToBytes(value);
			env.put(key, temp);
		}
		catch (Exception ex) {
			throw new RuntimeException("Could not convert " + key + " to byte[]: " + ex.getMessage());
		}
	}

	public void logSpecMessage(SpecMessage message) {
		CrystalUtils.print_info(message.toString());
	}

	public void processVar(String key, String value) throws IOException {
		if (resolveNext) {
			List     vals = new LinkedList();
			Iterator i    = CrystalUtils.toList(value).iterator();
			while (i.hasNext()) {
				String partial = (String)i.next();
                                File   temp    = new File(partial);

				vals.add(temp.getCanonicalPath());
			}

			env.put(key, String.join(", ", vals));
			resolveNext = false;
		}
		else {
			env.put(key, value);
		}
	}

	protected String[] breakApart(String val) {
		StringBuffer key   = new StringBuffer();
		StringBuffer value = new StringBuffer();

		/* parse our key */
		int x = 0;
		for (; true; x++) {
			if (x >= val.length()) {
				throw new RuntimeException("Argument " + val + " is not $KEY=######## or %var=\"string\" format.");
			}
			else if (val.charAt(x) == '=') {
				x++;
				break;
			}
			else {
				key.append(val.charAt(x));
			}
		}

		/* parse our value */
		for (; x < val.length(); x++) {
			value.append(val.charAt(x));
		}

		/* grab our key/value pair */
		String rv[] = new String[2];
		rv[0] = key.toString();
		rv[1] = value.toString();

		return rv;
	}

	public void processArg(String arg) throws SpecParseException, SpecProgramException, IOException {
		/* validate that our next -r argument is a %var */
		if (resolveNext && !arg.startsWith("%"))
			throw new RuntimeException("-r must be followed by %key=value");

		if ("".equals(arg)) {
			return;
		}
		else if (arg.startsWith("@")) {
			processInclude(arg.substring(1));
			return;
		}
		/* implement resolvenext */
		else if (arg.equals("-r")) {
			resolveNext = true;
			return;
		}
		/* implement resolvenext */
		else if (arg.startsWith("-r")) {
			resolveNext = true;
			processArg(arg.substring(2));
			return;
		}

		String split[] = breakApart(arg);

		if (split[0].startsWith("$")) {
			processKey(split[0], split[1]);
		}
		else if (split[0].startsWith("%")) {
			processVar(split[0], split[1]);
		}
		else if (split[0].equals("") && arg.startsWith("=")) {
			throw new RuntimeException("Key is empty in " + arg + " - try escaping \\$");
		}
		else {
			processKey("$" + split[0], split[1]);
		}
	}

	public byte[] run() throws SpecParseException, SpecProgramException, IOException {
		LinkSpec spec = LinkSpec.Parse(specfile);
		spec.addLogger(this);
		return spec.run(capability, env);
	}

	protected static String varHelp() {
		return  "\n\n\tUse @config.spec to run a specification file to configure variables for the specified .spec file." +

			"\n\n\tYou may also specify (optional) $DATAVARS and %VARIABLES on the command-line." +

			"\n\n    $DATAVAR example:" +
			"\n\n\t\\$A=04030201 places { 0x04, 0x03, 0x02, 0x01 } into $A." +
			"\n\n\tYou may need to escape $A with \\" +
			"\n\n\tTake care with the native byte order when specifying ints/longs/pointers" +

			"\n\n    %VARIABLE example:" +
			"\n\n\t%key=value sets %key to the string \"value\"" +
			"\n\n\tUse -r %key=value to resolve the paths in %key relative to $CWD";
	}

	public static void main(String args[]) {
		/* check that we have a verb! */
		if (args.length == 0) {
			CrystalUtils.print_error("Please use ./link or ./piclink");
			return;
		}

		/* check our verb, make sure it's piclink or link */
		String command = args[0];
		if (! CrystalUtils.toSet("run, buildPic").contains(command) ) {
			CrystalUtils.print_error("Unrecognized verb '" + command + "'");
			return;
		}

		/* check our number of arguments */
		if (args.length < 4) {
			if ("run".equals(command)) {
				CrystalUtils.print_error("./link [/path/to/loader.spec] [/path/to/file.dll|file.o] [out.bin] <\\$A=...> <%B=...> @C.spec" +
					"\n\tApply the specified .spec file to build a PIC DLL or COFF loader for file" +
					varHelp());
			}
			else if ("buildPic".equals(command)) {
				CrystalUtils.print_error("./piclink [/path/to/build.spec] <name.>[x86|x64] [out.bin] <\\$A=...> <%B=...> @C.spec" +
					"\n\tRun the specified .spec file to assemble a PIC program" +
					varHelp());
			}
			return;
		}

		try {
			/* instantiate our capability */
			Capability capability = null;

			if ("run".equals(command))
				capability = Capability.Parse(CrystalUtils.readFromFile(args[2]));
			else if ("buildPic".equals(command))
				capability = Capability.None(args[2]);

			/* instantiate our linker program */
			LinkerCLI program = new LinkerCLI(args[1], capability);

			/* handle all of our arguments! */
			for (int x = 4; x < args.length; x++) {
				program.processArg(args[x]);
			}

			/* process the spec */
			CrystalUtils.writeToFile(args[3], program.run() );
		}
		catch (SpecParseException specex) {
			CrystalUtils.print_error(specex.toString());
		}
		catch (SpecProgramException progex) {
			CrystalUtils.print_error(progex.toString());
		}
		catch (RuntimeException runex) {
			CrystalUtils.print_error(runex.getMessage());
		}
		catch (IOException ex) {
			CrystalUtils.reportException(ex);
		}
	}
}
