package crystalpalace.export;

public class ParseImport {
	protected String  symbol;
	protected boolean valid;
	protected String  module;
	protected String  function;

	public ParseImport(String symbol) {
		this.symbol = symbol;
		parse();
	}

	public static long ROR(long hash, int bits) {
		long a = hash >>> bits;
		long b = hash << (32 - bits);
		return (a | b) & 0xFFFFFFFFL;
	}

	public static int hash(byte[] hashme) {
		long  res = 0;

		for (int x = 0; x < hashme.length; x++) {
			res  = ROR(res, 13);
			res += hashme[x];
		}

		return (int)res;
	}

	public boolean isValid() {
		return valid;
	}

	public String getModule() {
		return module;
	}

	/*
	 * PICO DFR (and PIC DFR, I guess) allow GetProcAddress and LoadLibraryA without a KERNEL$
	 * prefix. For PICOs, we depend on the module value staying naked. But, for PIC, we need the
	 * module value populated. So, this function is for the DFR PIC BTF (ResolveAPI) to populate
	 * the module value for GetProcAddress/LoadLibraryA or otherwise throw an error when no module
	 * is present.
	 */
	public void checkAndPopulateModule() {
		if (!"".equals(module))
			return;

		if ("GetProcAddress".equals(function) || "LoadLibraryA".equals(function)) {
			module = "KERNEL32";
		}
		else {
			throw new RuntimeException("Function " + function + " is not in MODULE$Function format");
		}
	}

	/*
	 * Get the ROR-hash of this module, BUT it's a different ballgame from the function hash. We have
	 * to normalize it somewhat to calculate the same ROR-hash as our EAT/IAT walking code might:
	 *
	 * 1. We make the module all uppercase (our EAT/IAT walking code should do this too!)
	 * 2. We add .DLL (e.g., KERNEL32.DLL) because the hashed field has that value in it
	 * 3. We convert it to UTF-16LE so we ROR-hash the 0-byte values too.
	 *
	 * See: https://tradecraftgarden.org/simple.html?file=resolve_eat.h for an example of how this
	 * hashing works. Approx. line 44. We're walking BaseDllName.pBuffer
	 *
	 * This is compatible with Stephen Fewer's ReflectiveDLL and the Metasploit Framwork. Compatible
	 * means same constant-value IOCs (who doesn't love that)--but that's what +mutate is for
	 */
	public int getModuleHash() {
		return hash(crystalpalace.util.CrystalUtils.toBytes(getModule().toUpperCase() + ".DLL", "UTF-16LE"));
	}

	public String getFunction() {
		return function;
	}

	/*
	 * Get the ROR-hash of this function. Same algorithm commonly used in the Metasploit Framework and
	 * Stephen Fewer's ReflectiveDLL. We hash ASCII function strings to look for a match when walking
	 * the EAT or IAT to resolve Win32 APIs
	 *
	 * Algorithm and tables of function hashes here: https://github.com/ihack4falafel/ROR13HashGenerator
	 */
	public int getFunctionHash() {
		return hash(getFunction().getBytes());
	}

	public String getSymbol() {
		return symbol;
	}

	protected void parse() {
		/* start here */
		valid = false;

		String work;

		/* check and get rid of __imp__ and __imp_ */
		if (symbol.startsWith("__imp__")) {
			work = symbol.substring("__imp__".length());
		}
		else if (symbol.startsWith("__imp_")) {
			work = symbol.substring("__imp_".length());
		}
		else {
			return;
		}

		/* parse MODULE$Function */
		String parse[] = work.split("\\$");
		if (parse.length == 2) {
			module   = parse[0];
			function = parse[1];
		}
		else {
			module   = "";
			function = work;
		}

		/* get rid of the @# stuff at the end */
		parse = function.split("\\@");
		if (parse.length == 2) {
			function = parse[0];
			if(!module.isEmpty() && function.charAt(0) == '_'){
				function = function.substring(1);
			}
		}

		valid = true;
	}

	public String getTarget() {
		return module + "$" + function;
	}

	public String toString() {
		if (isValid()) {
			return symbol + ", " + module + ", " + function;
		}
		else {
			return symbol + " (not an import)";
		}
	}
}