package crystalpalace.spec;

import crystalpalace.util.*;

import java.util.*;

public class SpecVars implements VarStore {
	protected Map          globals;
	protected Map          locals;

	public SpecVars(Map globals, Map locals) {
		this.globals = globals;
		this.locals  = locals;
	}

	protected Map getContainer(String r) {
		if (locals.containsKey(r))
			return locals;

		if (globals.containsKey(r))
			return globals;

		throw new RuntimeException("Variable " + r + " is not set");
	}

	public String shift(String var) {
		Map    cont = getContainer(var);

		List  values = CrystalUtils.toList((String)cont.get(var));
		if (values.isEmpty())
			return null;

		String result = (String)values.remove(0);
		cont.put(var, String.join(", ", values));

		return result;
	}

	public String resolve(String r) {
		return (String)getContainer(r).get(r);
	}
}
