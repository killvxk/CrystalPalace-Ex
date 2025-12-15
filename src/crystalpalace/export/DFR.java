package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import java.util.*;

/* track the DFR resolver(s) associated with this specific PIC */
public class DFR {
	protected List         resolvers   = new LinkedList();
	protected Resolver     defresolver = null;
	protected ExportObject object;

	public static class Resolver {
		public String function;
		public String method;
		public Set    modules;

		public Resolver(String func, String method, String _modules) {
			this.function = func;
			this.method   = method;

			if (_modules == null)
				this.modules = new HashSet();
			else
				this.modules = CrystalUtils.toSet(_modules.toUpperCase());
		}

		public String getFunction() {
			return function;
		}

		public String getMethod() {
			return method;
		}

		public boolean validFor(String module) {
			return modules.contains(module.toUpperCase());
		}

		public boolean isRor13() {
			return "ror13".equals(method);
		}

		public boolean isStrings() {
			return "strings".equals(method);
		}

		public String toString() {
			if (modules.size() == 0)
				return "Resolver " + function + " (" + method + ")";
			else
				return "Resolver " + function + " (" + method + ") for " + modules;
		}
	}

	public DFR(ExportObject object) {
		this.object = object;
	}

	protected List getResolvers() {
		List _resolvers = new LinkedList(resolvers);
		if (defresolver != null)
			_resolvers.add(defresolver);
		return _resolvers;
	}

	public Set getResolverFunctions() {
		Set result = new HashSet();

		Iterator i = getResolvers().iterator();
		while (i.hasNext()) {
			Resolver r = (Resolver)i.next();
			result.add(r.getFunction());
		}

		return result;
	}

	protected void check(String symbol, String method) {
		Symbol temp = (Symbol)object.object.getSymbol(symbol);

		if (temp == null)
			throw new RuntimeException("Symbol " + symbol + " does not exist.");

		if (!temp.isFunction())
			throw new RuntimeException("Symbol " + symbol + " is not a function.");

		/* check that we haven't double defined the same resolver to different contracts */
		Iterator i = getResolvers().iterator();
		while (i.hasNext()) {
			Resolver next = (Resolver)i.next();
			if ( symbol.equals(next.getFunction()) && !method.equals(next.getMethod()) )
				throw new RuntimeException(next + " uses a different contract for function " + next.getFunction());
		}
	}

	public void addResolver(String func, String method, String mods) {
		check(func, method);
		resolvers.add(new Resolver(func, method, mods));
	}

	public void setDefaultResolver(String func, String method) {
		check(func, method);
		defresolver = new Resolver(func, method, null);
	}

	public Resolver getResolver(ParseImport imp) {
		Iterator i = resolvers.iterator();
		while (i.hasNext()) {
			Resolver next = (Resolver)i.next();
			if ( next.validFor(imp.getModule()) )
				return next;
		}

		if (defresolver != null)
			return defresolver;

		throw new RuntimeException("No DFR resolver matches " + imp.getSymbol());
	}

	public boolean hasResolvers() {
		return resolvers.size() > 0 || defresolver != null;
	}
}
