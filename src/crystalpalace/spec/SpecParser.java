package crystalpalace.spec;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import crystalpalace.export.*;

import java.util.*;
import java.io.*;

public class SpecParser {
	protected static Set  commands   = CrystalUtils.toSet("export, generate, patch, preplen, prepsum, link, load, make, push, xor, rc4, run, import, disassemble, coffparse, merge, reladdr, dfr, fixptrs, mergelib, fixbss, remap, attach, redirect, preserve, addhook, filterhooks, protect, exportfunc, optout, set, setg, foreach, echo, call, modcall, resolve, pack, linkfunc, next");
	protected static Set  commandsv  = CrystalUtils.toSet("push, xor, rc4, filterhooks");
	protected static Set  fullcmds   = CrystalUtils.toSet("make coff, make object, make pic, make pic64, export, preplen, prepsum, merge");
	protected static Set  arg1cmds   = CrystalUtils.toSet("import, link, load, disassemble, coffparse, reladdr, fixptrs, fixbss, mergelib, protect, addhook, linkfunc");
	protected static Set  arg2cmds   = CrystalUtils.toSet("attach, redirect, remap, preserve, addhook, exportfunc, optout, load, patch");
	protected static Set  labels     = CrystalUtils.toSet("x86, x64, x86.o, x64.o, x86.dll, x64.dll");

	protected static Set  options    = CrystalUtils.toSet("+optimize,+disco,+mutate,+gofirst,+blockparty,+shatter,+regdance");
	protected static Set  optcmds    = CrystalUtils.toSet("make object, make pic, make pic64, make coff");

	protected LinkSpec spec   = null;
	protected List     errors = new LinkedList();
	protected String   parent = "";

	public LinkSpec getSpec() {
		return spec;
	}

	public List getErrors() {
		return errors;
	}

	public void error(String msg, int lineNo) {
		errors.add(msg + " at line " + lineNo);
	}

	/* simplify our number parser and error reporting logic */
	public int parseInt(CommandParser parser, String arg, int x) {
		int val = CrystalUtils.parseInt(arg, -1);

		if (val == -1)
			error("Invalid argument for '" + parser.getOriginal() + "'. " + arg + " is not a valid number", (x + 1));
		else if (val < 0)
			error("Invalid argument for '" + parser.getOriginal() + "'. " + arg + " is a negative number", (x + 1));
		else if (val == 0)
			error("Invalid argument for '" + parser.getOriginal() + "'. " + arg + " is zero", (x + 1));

		return val;
	}

	public boolean isValidLabel(String name) {
		Iterator i = labels.iterator();
		while (i.hasNext()) {
			String cand = (String)i.next();
			if (name.equals(cand) || name.endsWith("." + cand)) {
				return true;
			}
		}

		return false;
	}

	public void parse(String content, String parent) {
                         spec     = new LinkSpec(this, parent);
		String   label    = "";

		String contents[] = content.split("\\n");
		for (int x = 0; x < contents.length; x++) {
			/* trim out any whitespace */
			contents[x] = contents[x].trim();

			/* handle comments */
			if (contents[x].startsWith("#"))
				continue;

			/* carry on! */
			if (contents[x].endsWith(":") && contents[x].indexOf(' ') == -1) {
				/* it's a label */
				label = contents[x].substring(0, contents[x].length() - 1);

				/* check that it's a valid label! */
				if (label.startsWith("."))
					error("Invalid label '" + label + "' - acceptable labels are <name.>[x86|x64]<.o|.dll> - don't start name with a '.'", x + 1);
				else if (!isValidLabel(label))
					error("Invalid label '" + label + "' - acceptable labels are <name.>[x86|x64]<.o|.dll>", x + 1);

				/* check that it's a new label */
				if (spec.program.targets(label))
					error("Label " + label + " is already defined", x + 1);

				continue;
			}
			else if ("".equals(contents[x])) {
				/* it's whitespace, do nothing */
				continue;
			}

			CommandParser parser  = new CommandParser(contents[x]);
			String        command = parser.getCommand();
			String        args[]  = parser.getArguments();

			parseOne(label, contents[x], x);
		}
	}

	public void parseOne(String label, String content, int x) {
		CommandParser parser  = new CommandParser(content);
		String        command = parser.getCommand();
		String        args[]  = parser.getArguments();

		if ("describe".equals(command)) {
			if (parser.getArguments().length == 0)
				error("Command '" + command + "' requires an argument", x + 1);
			else
				spec.description = parser.getArguments()[0];
		}
		else if ("author".equals(command)) {
			if (parser.getArguments().length == 0)
				error("Command '" + command + "' requires an argument", x + 1);
			else
				spec.author = parser.getArguments()[0];
		}
		else if ("name".equals(command)) {
			if (parser.getArguments().length == 0)
				error("Command '" + command + "' requires an argument", x + 1);
			else
				spec.name = parser.getArguments()[0];
		}
		else if ("".equals(label)) {
			/* no top-level label? That's a (fatal) error! */
			error("Commands must exist under an 'x86:' or 'x64:' label", (x + 1));
			return;
		}
		else {
			int           alen    = parser.getArguments().length;

			/* shortcut for a local call */
			if (command.startsWith(".")) {
				spec.program.getInstructionsForLabel(label).add(parser);
				return;
			}
			/* if we don't recognize the command, bail */
			else if (!commands.contains(command)) {
				error("Invalid command '" + command + "'", (x + 1));
				return;
			}

			/* check if we're correct, first */
			if ( fullcmds.contains(parser.getFullCommand()) ) {
				//parser.setOption("+mutate");
				spec.program.getInstructionsForLabel(label).add(parser);
				return;
			}
			else if (parser.hasOptions()) {
				/* check that our options are valid */
				Iterator i = parser.getOptions().iterator();
				while (i.hasNext()) {
					String opt = (String)i.next();
					if (!options.contains(opt))
						error("Invalid option " + opt + " for '" + parser.getFullCommand() + "'", (x + 1));
				}

				/* check that the command accepts options */
				if ( optcmds.contains(parser.getFullCommand()) ) {
					//parser.setOption("+mutate");
					spec.program.getInstructionsForLabel(label).add(parser);
				}
				else {
					error("Command '" + command + "' does not accept +options " + parser.getOptions(), (x + 1));
				}

				return;
			}
			else if (commandsv.contains(command) && alen == 1) {
				spec.program.getInstructionsForLabel(label).add(parser);
				return;
			}
			else if (arg1cmds.contains(command) && alen == 1) {
				spec.program.getInstructionsForLabel(label).add(parser);
				return;
			}
			else if (arg2cmds.contains(command) && alen == 2) {
				spec.program.getInstructionsForLabel(label).add(parser);
				return;
			}
			else if ("dfr".equals(command) && (alen == 2 || alen == 3)) {
				Set valid = CrystalUtils.toSet("ror13, strings");

				if (valid.contains(args[1])) {
					spec.program.getInstructionsForLabel(label).add(parser);
				}
				else {
					error("Invalid method '" + args[1] + "' for '" + parser.getOriginal() + "'. Use 'ror13' or 'strings'", (x + 1));
				}

				return;
			}
			else if ("generate".equals(command) && alen == 2) {
				int val = parseInt(parser, args[1], x);

				if (val != -1) {
					spec.program.getInstructionsForLabel(label).add(parser);
				}

				return;
			}
			else if ("pack".equals(command) && alen >= 2) {
				spec.program.getInstructionsForLabel(label).add(parser);
				return;
			}
			else if ("resolve".equals(command) && alen == 1) {
				String types[] = parser.getTypes();

				if (!args[0].startsWith("%")) {
					error("Invalid argument for '" + parser.getOriginal() + "'. Try \"%" + args[0] + "\"", (x + 1));
				}
				else if (! "string".equals(types[0]) ) {
					error("Quotes required for variable: " + command + " \"" + args[0] + "\"", (x + 1));
				}
				else {
					spec.program.getInstructionsForLabel(label).add(parser);
				}

				return;
			}
			else if (CrystalUtils.toSet("set, setg").contains(command) && alen == 2) {
				String types[] = parser.getTypes();

				if (!args[0].startsWith("%")) {
					error("Invalid argument for '" + parser.getOriginal() + "'. Try \"%" + args[0] + "\"", (x + 1));
				}
				else if (! "string".equals(types[0]) ) {
					error("Quotes required for variable: " + command + " \"" + args[0] + "\"", (x + 1));
				}
				else {
					spec.program.getInstructionsForLabel(label).add(parser);
				}

				return;
			}
			else if ("run".equals(command) && alen >= 1) {
				spec.program.getInstructionsForLabel(label).add(parser);
				return;
			}
			else if ("call".equals(command) && alen >= 2) {
				if (args[1].startsWith(".")) {
					error("Invalid label for '" + parser.getOriginal() + "' - callable labels do not begin with a '.'", (x + 1));
				}
				else {
					spec.program.getInstructionsForLabel(label).add(parser);
				}
				return;
			}
			else if ("echo".equals(command) && alen >= 1) {
				spec.program.getInstructionsForLabel(label).add(parser);
				return;
			}
			else if ("foreach".equals(command) && alen == 2) {
				spec.program.getInstructionsForLabel(label).add(parser);
				parseOne(label, args[1], x);
				return;
			}
			else if ("next".equals(command) && alen == 2) {
				String types[] = parser.getTypes();

				if (!args[0].startsWith("%")) {
					error("Invalid argument for '" + parser.getOriginal() + "'. Try \"%" + args[0] + "\"", (x + 1));
				}
				else if (! "string".equals(types[0]) ) {
					error("Quotes required for variable: " + command + " \"" + args[0] + "\"", (x + 1));
				}
				else {
					spec.program.getInstructionsForLabel(label).add(parser);
					parseOne(label, args[1], x);
				}

				return;
			}

			/* start preparing our error message about the command, start with correct use hint */
			String hint = "";
			if ("export".equals(command)) {
				hint = "export";
			}
			else if ("generate".equals(command)) {
				hint = "generate $KEY 1024";
			}
			else if ("link".equals(command)) {
				hint = "link 'section_name'";
			}
			else if ("linkfunc".equals(command)) {
				hint = "linkfunc 'symbol'";
			}
			else if ("load".equals(command)) {
				hint = "load 'path/to/file'";
			}
			else if ("run".equals(command)) {
				hint = "run 'path/to/file' [args...]";
			}
			else if ("call".equals(command)) {
				hint = "call 'path/to/file' 'name' [args...]";
			}
			else if ("mergelib".equals(command)) {
				hint = "mergelib 'path/to/file.zip'";
			}
			else if ("disassemble".equals(command)) {
				hint = "disassemble out.txt";
			}
			else if ("coffparse".equals(command)) {
				hint = "coffparse out.txt";
			}
			else if ("make".equals(command)) {
				hint = "make pic, make coff, make object";
			}
			else if ("push".equals(command)) {
				hint = "push $DLL";
			}
			else if ("xor".equals(command)) {
				hint = "xor $KEY";
			}
			else if ("rc4".equals(command)) {
				hint = "rc4 $KEY";
			}
			else if ("patch".equals(command)) {
				hint = "patch 'symbol' $VAR";
			}
			else if ("import".equals(command)) {
				hint = "import 'LoadLibraryA, GetProcAddress, ...'";
			}
			else if ("reladdr".equals(command)) {
				hint = "reladdr '_go'";
			}
			else if ("dfr".equals(command)) {
				hint = "dfr 'resolver_func' 'ror13|strings' ['mod1, mod2']";
			}
			else if ("fixptrs".equals(command)) {
				hint = "fixptrs '_getretaddr'";
			}
			else if ("fixbss".equals(command)) {
				hint = "fixbss 'getbssaddr'";
			}
			else if ("remap".equals(command)) {
				hint = "remap 'old_symbol' 'new_symbol'";
			}
			else if ("redirect".equals(command)) {
				hint = "redirect 'target' 'hook'";
			}
			else if ("attach".equals(command)) {
				hint = "attach 'MODULE$function' 'hook'";
			}
			else if ("preserve".equals(command)) {
				hint = "preserve 'target|MODULE$Function' 'func1, func2, func3'";
			}
			else if ("addhook".equals(command)) {
				hint = "addhook 'MODULE$Function' ['hook']";
			}
			else if ("filterhooks".equals(command)) {
				hint = "filterhooks $DLL|$OBJECT";
			}
			else if ("protect".equals(command)) {
				hint = "protect 'func1, func2, etc.'";
			}
			else if ("exportfunc".equals(command)) {
				hint = "exportfunc 'function' '__tag_function'";
			}
			else if ("optout".equals(command)) {
				hint = "optout 'target' 'hook1, hook2, hook3'";
			}
			else if ("setg".equals(command)) {
				hint = "setg '%var' 'value'";
			}
			else if ("set".equals(command)) {
				hint = "set '%var' 'value'";
			}
			else if ("resolve".equals(command)) {
				hint = "resolve '%var'";
			}
			else if ("foreach".equals(command)) {
				hint = "foreach 'val1, val2': command %_";
			}
			else if ("next".equals(command)) {
				hint = "next '%var': command %_";
			}
			else if ("echo".equals(command)) {
				hint = "echo 'message'";
			}
			else if ("pack".equals(command)) {
				hint = "pack $DEST 'template' 'arg1' 'arg2' ...";
			}

			/* report the error */
			if (alen == 0) {
				error("Command " + command + " missing arguments, try '" + hint + "'", (x + 1));
			}
			else {
				error("Command " + command + " invalid arguments, try '" + hint + "'", (x + 1));
			}
		}
	}
}
