package crystalpalace.util;

import java.util.*;

/* A beautiful and flexible command parser with a means to on-the-fly redefine the quote character */
public class CommandParser {
	protected String   original;
	protected char     quotechar = '"';
	protected String   command   = "";
	protected Set      options   = new HashSet();
	protected List     tokens    = new LinkedList();

	private static class Token {
		protected String text;
		protected int    type;

		public static final int T_VAR    = 0;
		public static final int T_STRING = 1;
		public static final int T_CONCAT = 2;

		protected Token(String text, int type) {
			this.text = text;
			this.type = type;
		}

		public static Token String(String text) {
			return new Token(text, T_STRING);
		}

		public static Token Var(String text) {
			return new Token(text, T_VAR);
		}

		public static Token Concat() {
			return new Token("<>", T_CONCAT);
		}

		public void getType(ListIterator l, LinkedList x) {
			if (type == T_STRING) {
				x.add("string");
			}
			else if (type == T_VAR) {
				x.add("var");
			}
			else if (type == T_CONCAT) {
				String last = (String)x.removeLast();
				String next = "";

				if (l.hasNext()) {
					((Token)l.next()).getType(l, x);
					next = (String)x.removeLast();
				}

				x.add(last + " <> " + next);
			}
		}

		public void apply(Map args, ListIterator l, LinkedList x, VarStore v) {
			if (type == T_STRING) {
				x.add(text);
			}
			else if (type == T_VAR) {
				String value = v.resolve(text);

				x.add(value);
				args.put(text, value);
			}
			else if (type == T_CONCAT) {
				String last = (String)x.removeLast();
				String next = "";

				if (l.hasNext()) {
					((Token)l.next()).apply(args, l, x, v);
					next = (String)x.removeLast();
				}

				x.add(last + next);
			}
		}

		public String toString() {
			switch (type) {
				case T_VAR:
					return "(T_VAR " + text + ")";
				case T_STRING:
					return "(T_STR " + text + ")";
				case T_CONCAT:
					return "(T_CAT)";
			}

			return "(T_UNK " + type + " " + text + ")";
		}
	}

	public CommandParser(String text) {
		this.original = text;

		work(text);
	}

	public char getQuoteCharacter() {
		return quotechar;
	}

	public String getOriginal() {
		return original;
	}

	public String getCommand() {
		return command;
	}

	public String[] getTypes() {
		/* build our arguments account for var resolutions and <> concatenation */
		LinkedList   build = new LinkedList();
		ListIterator liter = tokens.listIterator();
		while (liter.hasNext()) {
			Token next = (Token)liter.next();
			next.getType(liter, build);
		}

		/* types array */
		String types[] = new String[build.size()];

		Iterator i = build.iterator();
		for (int z = 0; i.hasNext(); z++) {
			types[z] = (String)i.next();
		}

		return types;
	}

	public static class Arguments {
		public String[] args;
		public Map      vars = new LinkedHashMap();
	}

	public Arguments getArguments(VarStore store) {
		Arguments result = new Arguments();

		/* build our arguments account for var resolutions and <> concatenation */
		LinkedList   build = new LinkedList();
		ListIterator liter = tokens.listIterator();
		while (liter.hasNext()) {
			Token next = (Token)liter.next();
			next.apply(result.vars, liter, build, store);
		}

		/* convert the arguments to an array eh?!? */
		result.args = new String[build.size()];

		Iterator i = build.iterator();
		for (int z = 0; i.hasNext(); z++) {
			result.args[z] = (String)i.next();
		}

		return result;
	}

	public String[] getArguments() {
		return getArguments(new VarStore() {
			public String resolve(String var) {
				return var;
			}
		}).args;
	}

	public String getFullCommand() {
		String args[] = getArguments();
		String copy[] = new String[args.length+1];
		copy[0]       = command;

		for (int x = 0; x < args.length; x++) {
			if (args[x].indexOf(" ") == -1) {
				copy[x+1] = args[x];
			}
			else {
				copy[x+1] = quotechar + args[x] + quotechar;
			}
		}

		return String.join(" ", copy);
	}

	public boolean hasOptions() {
		return options.size() > 0;
	}

	public Set getOptions() {
		return options;
	}

	public void setOption(String name) {
		options.add(name);
	}

	public boolean isOption(String name) {
		return options.contains(name);
	}

	public void addToken(List tokens, String token, boolean quoted) {
		/* We ALWAYS add "quoted" strings as what they are quoted as */
		if (quoted) {
			tokens.add(Token.String(token));
		}
		/* this is a +option... track this separately */
		else if (token.startsWith("+") && token.indexOf(" ") == -1) {
			options.add(token);
		}
		/* this is a variable that needs to get resolved */
		else if (token.startsWith("%") && token.indexOf(" ") == -1) {
			tokens.add(Token.Var(token));
		}
		/* concat operator */
		else if (token.equals("<>")) {
			tokens.add(Token.Concat());
		}
		/* We just assume it's a string otherwise */
		else {
			tokens.add(Token.String(token));
		}
	}

	public void work(String argz) {
		options             = new HashSet();
		StringBuffer token  = new StringBuffer();

		/* trim the whitespace as it doesn't help us either way */
		argz = argz.trim();

		/* let's figure out our command */
		int x = 0;
		for (; x < argz.length(); x++) {
			char temp = argz.charAt(x);

			if (Character.isWhitespace(temp)) {
				command = token.toString();
				token   = new StringBuffer();
				break;
			}
			else if (temp == ',' && (x + 2) < argz.length() && Character.isWhitespace( argz.charAt(x + 2) )) {
				command   = token.toString();
				quotechar = argz.charAt(x + 1);
				token     = new StringBuffer();
				x += 2;
				break;
			}
			else if (temp == ',' && (x + 2) == argz.length()) {
				command   = token.toString();
				quotechar = argz.charAt(x + 1);
				token     = new StringBuffer();
				x += 2;
				break;
			}
			else {
				token.append(temp);
			}
		}

		/* No command? Well then, probably no arguments either */
		if ("".equals(command)) {
			command = argz;
			return;
		}

		/* keep walking this way to build our arguments */
		for (; x < argz.length(); x++) {
			char temp = argz.charAt(x);
			if (Character.isWhitespace(temp)) {
				if (token.length() > 0)
					addToken(tokens, token.toString(), false);
				token = new StringBuffer();
			}
			/* we treat : like a space and like a "we're done taking new arguments, group EVERYTHING" */
			else if (temp == ':') {
				if (token.length() > 0)
					addToken(tokens, token.toString(), false);

				token = new StringBuffer();

				for (x++ ; x < argz.length(); x++) {
					token.append(argz.charAt(x));
				}

				if (token.length() > 0)
					addToken(tokens, token.toString().trim(), true);

				return;
			}
			/* we treat a stand-alone # as a comment and we're fully done if we see this */
			else if (temp == '#') {
				if (token.length() > 0)
					addToken(tokens, token.toString(), false);

				return;
			}
			else if (temp == quotechar && token.length() == 0) {
				for (x++ ; x < argz.length() && argz.charAt(x) != quotechar; x++) {
					token.append(argz.charAt(x));
				}

				addToken(tokens, token.toString(), true);
				token = new StringBuffer();
			}
			else {
				token.append(temp);
			}
		}

		/* do we have a staggler? add it! */
		if (token.length() > 0)
			addToken(tokens, token.toString(), false);
	}

	public String toString() {
		StringBuffer result = new StringBuffer();
		result.append("Original:   >>>" + original + "<<<\n");
		result.append("Command:    >>>" + command + "<<<\n");
		result.append("Quote Char: >>>" + quotechar + "<<<\n");
		result.append("Full Command: >" + getFullCommand() + "<<<\n");
		result.append("Options:       " + options + "\n");

		Iterator i = tokens.iterator();
		while (i.hasNext()) {
			String token = ((Token)i.next()).toString();
			result.append(token + "\n");
		}

		return result.toString();
	}

	public static void main(String args[]) {
		CommandParser parser = new CommandParser(args[0]);
		System.out.println(parser.toString());

		String argz[] = parser.getArguments(new VarStore() {
			public String resolve(String var) {
				if ("%foo".equals(var))
					return "foo!";
				else if ("%date".equals(var)) {
					return "20251119.1634";
				}
				else {
					return "Unk " + var;
				}
			}
		}).args;

		for (int x = 0; x < argz.length; x++) {
			System.out.println("args[" + x + "] = " + argz[x]);
		}
	}
}
