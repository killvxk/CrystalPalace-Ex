package crystalpalace.spec;

import java.util.*;

/*
 * A store of function -> symbol -> tag mappings for PICO exports.
 *
 * We need this to be global within a .spec program so that any component of our built program
 * can access this information.
 *
 * It feels convoluted, but this is the best I could come up with (short notice) to prevent the
 * user from having to manually carry the tag over to the program. At least with this system
 * there's a declared exported function and the linker can throw an error if there isn't.
 */
public class TagStore {
	protected Set tags    = new HashSet();
	protected Map funcs   = new HashMap();
	protected Map symbols = new HashMap();

	public static class Tag {
		protected String function;
		protected String symbol;
		protected int    tagNo;

		public Tag(String function, String symbol, int tagNo) {
			this.function = function;
			this.symbol   = symbol;
			this.tagNo    = tagNo;
		}

		public int getTag() {
			return tagNo;
		}

		public String getSymbol() {
			return symbol;
		}

		public String toString() {
			return "Tag " + function + " -> " + symbol + " -> " + tagNo;
		}

		public boolean is(String _function, String _symbol) {
			return function.equals(_function) && symbol.equals(_symbol);
		}
	}

	protected int getUniqueTag() {
		int cand;

		while (true) {
			cand = (int)(Math.random() * 65534);
			if (!tags.contains(cand))
				break;
		}

		tags.add(cand);

		return cand;
	}

	public TagStore() {
	}

	public Tag getSymbolTag(String symbol) {
		return (Tag)symbols.get(symbol);
	}

	public Tag getFunctionTag(String function) {
		return (Tag)funcs.get(function);
	}

	public List getTags() {
		return new LinkedList(funcs.values());
	}

	public void register(String function, String symbol, boolean x64) {
		if (x64) {
			if (!symbol.startsWith("__tag_"))
				throw new RuntimeException("Symbol '" + symbol + "' must start with __tag_ (x64)");
		}
		else {
			if (!symbol.startsWith("___tag_"))
				throw new RuntimeException("Symbol '" + symbol + "' must start with ___tag_ (x86)");
		}

		Tag f  = getFunctionTag(function);
		if (f != null && !f.is(function, symbol))
			throw new RuntimeException("Function bound to another symbol " + f);

		Tag s  = getSymbolTag(symbol);
		if (s != null && !s.is(function, symbol))
			throw new RuntimeException("Symbol bound to another function " + s);

		Tag tag = new Tag(function, symbol, getUniqueTag());
		funcs.put(function, tag);
		symbols.put(symbol, tag);
	}
}
