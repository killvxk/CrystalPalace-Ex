package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.imports.*;
import crystalpalace.util.*;
import java.util.*;

/* track the instrumentation the user wants to apply */
public class Hooks {
	protected ExportObject object;
	protected Map          external  = new HashMap();
	protected Map          local     = new HashMap();
	protected Map          notouch   = new HashMap();
	protected Map          optout    = new HashMap();
	protected Set          protect   = new HashSet();
	protected Map          resolve   = new HashMap();
	protected int          HookIndex = 0;

	/* This is how we stack/allow recursive hooks. First hook becomes the default for everything
	 * else. Follow-on hooks only apply to the previous hook's context */
	public class HookChain {
		protected String target;
		protected Map    hooks = new LinkedHashMap();
		protected Map    cache = new HashMap();

		public HookChain(String target) {
			this.target = target;
		}

		public void add(Hook hook) {
			if ( hooks.containsKey(hook.getWrapper()) )
				throw new RuntimeException(hook + " already declared. Order matters. Please remove duplicate.");
			else
				hooks.put(hook.getWrapper(), hook);
		}

		public boolean eval(String context, Hook cand) {
			/* don't apply a hook to its own wrapper function */
			if (cand.getWrapper().equals(context)) {
				//CrystalUtils.print_error("Hook(" + context + ", " + target + ", " + cand.getWrapper() + ") = false (same context)");
				return false;
			}

			/* check if our context is opted out of this specific wrapper */
			if (isOptOut(context, cand.getWrapper())) {
				//CrystalUtils.print_error("Hook(" + context + ", " + target + ", " + cand.getWrapper() + ") = false (opt out)");
				return false;
			}

			/* IF the context is a hook declared AFTER our candidate, do not apply this hook */
			Hook temp = (Hook)hooks.get(context);
			if (temp != null && temp.getDeclaredIndex() > cand.getDeclaredIndex()) {
				//CrystalUtils.print_error("Hook(" + context + ", " + target + ", " + cand.getWrapper() + ") = false (comes later)");
				return false;
			}

			/* walk the chain of hooks and see if ANY call an opted out function */
			String next = resolve(cand.getWrapper());
			while (next != null) {
				if (isOptOut(context, next)) {
					//CrystalUtils.print_error("Hook(" + context + ", " + target + ", " + cand.getWrapper() + ") = false (calls " + next + ", optout)");
					return false;
				}

				next = resolve(next);
			}

			//CrystalUtils.print_good("Hook(" + context + ", " + target + ", " + cand.getWrapper() + ") = true");
			return true;
		}

		/* cache the resolve result, since the process is a little intense now */
		public String resolve(String context) {
			if (cache.containsKey(context))
				return (String)cache.get(context);

			String value = _resolve(context);
			cache.put(context, value);

			return value;
		}

		/*
		 * This is a per-context resolver. We know the target, the context. And the hook. Walk these in order!
		 */
		public String _resolve(String context) {
			/* highest level opt out, nothing is allowed to change this context. */
			if (protect.contains(context))
				return null;
			/* next highest level: if this target is preserved in this context... no hook */
			else if (isPreserved(target, context))
				return null;
			/* do not instrument the function we are hooking? */
			else if (target.equals(context))
				return null;

			/* walk the hooks and see if any pass the test */
			Iterator i = hooks.values().iterator();
			while (i.hasNext()) {
				Hook cand = (Hook)i.next();
				if (eval(context, cand))
					return cand.getWrapper();
			}

			//CrystalUtils.print_warn("Hook(" + context + ", " + target + ", *NULL*) = true");
			return null;
		}
	}

	public static class Hook {
		protected String target;
		protected String wrapper;
		protected int    index;

		public Hook(String target, String wrapper, int index) {
			this.target  = target;
			this.wrapper = wrapper;
			this.index   = index;
		}

		public String getTarget() {
			return target;
		}

		public String getWrapper() {
			return wrapper;
		}

		/* declaration order. Our way of knowing if one hook came after another one. That's all */
		public int getDeclaredIndex() {
			return index;
		}

		public String toString() {
			return "Hook " + target + " -> " + wrapper;
		}
	}

	public boolean isPreserved(String target, String func) {
		if (!notouch.containsKey(target))
			return false;

		return getSet(notouch, target).contains(func);
	}

	public boolean isOptOut(String target, String hook) {
		if (!optout.containsKey(target))
			return false;

		return getSet(optout, target).contains(hook);
	}

	public void attach(String target, String wrapper) {
		object.check(wrapper);
		new ModFunc(target); // we don't need the object, but this is a MODULE$Function format check
		getChain(external, target).add(new Hook(target, wrapper, HookIndex++));
	}

	public void redirect(String target, String wrapper) {
		object.check(wrapper);
		getChain(local, target).add(new Hook(target, wrapper, HookIndex++));
	}

	/*
	 * PRESERVE a specific target (e.g., MODULE$Function, localfunc) within the context of one or more
	 * functions. Use this for situations where a function absolutely expects and needs access to the
	 * original target.
	 */
	public void preserve(String target, String funcs) {
		Set preserveme = getSet(notouch, target);

		Iterator i = CrystalUtils.toSet(funcs).iterator();
		while (i.hasNext()) {
			String next = (String)i.next();
			object.check(next);
			preserveme.add(next);
		}
	}

	/*
	 * OPTOUT a function fron one or more hooks/wrappers. Use this within a tradecraft module that wants
	 * to exclude its own hooks within its setup routines but wants to remain open to other tradecraft
	 * composed over or under it.
	 */
	public void optout(String target, String hooks) {
		Set stayaway = getSet(optout, target);

		object.check(target);

		Iterator i = CrystalUtils.toSet(hooks).iterator();
		while (i.hasNext()) {
			String next = (String)i.next();
			object.check(next);
			stayaway.add(next);
		}
	}

	/*
	 * PROTECT one or more functions from ANY instrumentation. Use for debug functions and other
	 * things that we just don't want to mess with.
	 */
	public void protect(String funcs) {
		Iterator i = CrystalUtils.toSet(funcs).iterator();
		while (i.hasNext()) {
			String next = (String)i.next();
			//object.check(next);
			protect.add(next);
		}
	}

	public String getHook(String context, String target) {
		if (external.containsKey(target))
			return getChain(external, target).resolve(context);
		else
			return null;
	}

	public String getLocalHook(String context, String target) {
		if (local.containsKey(target))
			return getChain(local, target).resolve(context);
		else
			return null;
	}

	public boolean hasHooks() {
		return external.size() > 0;
	}

	public boolean hasLocalHooks() {
		return local.size() > 0;
	}

	protected HookChain getChain(Map map, String key) {
		if (!map.containsKey(key))
			map.put(key, new HookChain(key));

		return (HookChain)map.get(key);
	}

	protected HashSet getSet(Map map, String key) {
		if (!map.containsKey(key))
			map.put(key, new HashSet());

		return (HashSet)map.get(key);
	}

	protected static class ModFunc {
		public String module;
		public String function;

		public ModFunc(String target) {
			String parse[] = target.split("\\$");
			if (parse.length == 1)
				throw new RuntimeException(target + " is not in MODULE$Function format");

			this.module   = parse[0];
			this.function = parse[1];
		}

		public String getFunction() {
			return function;
		}

		public String getModule() {
			return module;
		}

		public String toString() {
			return module.toUpperCase() + "$" + function;
		}
	}

	public Hooks(ExportObject object) {
		this.object = object;

		if (object.object.getMachine().equals("x64"))
			protect.add("dprintf");
		else
			protect.add("_dprintf");
	}

	public static class ResolveHook {
		protected String  wrapper;
		protected ModFunc modfunc;

		public ResolveHook(String target, String wrapper) {
			this.wrapper  = wrapper;
			this.modfunc  = new ModFunc(target);
		}

		public int getFunctionHash() {
			return CrystalUtils.ror13(getFunction().getBytes());
		}

		public String getFunction() {
			return modfunc.getFunction();
		}

		public String getTarget() {
			return modfunc.toString();
		}

		public String getWrapper() {
			return wrapper;
		}

		public boolean isSelf() {
			return wrapper == null;
		}
	}

	public void addResolveHook(String target) {
		ResolveHook rhook = new ResolveHook(target, null);
		resolve.put(rhook.getFunction(), rhook);
	}

	public void addResolveHook(String target, String wrapper) {
		object.check(wrapper);
		ResolveHook rhook = new ResolveHook(target, wrapper);
		resolve.put(rhook.getFunction(), rhook);
	}

	public List getResolveHooks() {
		LinkedList result = new LinkedList();
		result.addAll(resolve.values());
		Collections.shuffle(result);
		return result;
	}

	public void filterResolveHooks(byte[] content) {
		Set imps = Imports.getImports(content).getStrings();

		Iterator i = resolve.values().iterator();
		while (i.hasNext()) {
			ResolveHook next = (ResolveHook)i.next();
			if (! imps.contains(next.getTarget()) )
				i.remove();
		}
	}
}
