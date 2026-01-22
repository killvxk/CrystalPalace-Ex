package crystalpalace.btf;

import crystalpalace.btf.pass.*;
import crystalpalace.btf.pass.hook.*;
import crystalpalace.btf.pass.easypic.*;
import crystalpalace.btf.pass.mutate.*;

import crystalpalace.export.*;
import crystalpalace.coff.*;
import crystalpalace.merge.*;
import crystalpalace.util.*;
import java.util.*;

public class Modify {
	protected COFFObject object;

	public Modify(COFFObject object) {
		this.object = object;
	}

	/*
	 * BTF pass 0... apply redirect, replace, and attach instrumentation
	 */
	public boolean hasIntrinsics() {
		/* If __resolve_hook is used, we want to run this pass to resolve the intrinsic */
		if (object.getSymbol("__resolve_hook") != null || object.getSymbol("___resolve_hook") != null)
			return true;

		/* walk the symbols and look for any __tag or ___tag symbols */
		Iterator i = object.getSymbols().keySet().iterator();
		while (i.hasNext()) {
			String next = (String)i.next();
			if (next.startsWith("__tag_") || next.startsWith("___tag_"))
				return true;
		}

		return false;
	}

	protected COFFObject resolveIntrinsics(Exports exports, Hooks hooks) {
		/* see if we have any intrinsic functions and save a pass */
		if (!hasIntrinsics())
			return object;

		/* let's analyze (disassemble) our code first */
		Code code = Code.Init(object).analyze();

		/* get a mapping of function -> disassembled instructions */
		Map funcs = code.getCodeByFunction();

		/* build up our passes */
		MultiModify pass = new MultiModify();
		pass.add(new ResolveHooks(code, hooks));
		pass.add(new ResolveTags(code, exports));

		/* let's apply this pass and rebuild the program */
		return new Rebuilder(code, funcs).rebuild(new RebuildConfig().adder(pass));
	}

	protected COFFObject applyWin32Hooks(Hooks hooks) {
		if (!hooks.hasHooks())
			return object;

		/* let's analyze (disassemble) our code first */
		Code code = Code.Init(object).analyze();

		/* get a mapping of function -> disassembled instructions */
		Map funcs = code.getCodeByFunction();

		/* let's apply this pass and rebuild the program */
		return new Rebuilder(code, funcs).rebuild(new RebuildConfig().adder(new Attach(code, hooks)));
	}

	protected COFFObject applyLocalHooks(Hooks hooks) {
		if (!hooks.hasLocalHooks())
			return object;

		/* let's analyze (disassemble) our code first */
		Code code = Code.Init(object).analyze();

		/* get a mapping of function -> disassembled instructions */
		Map funcs = code.getCodeByFunction();

		/* let's apply this pass and rebuild the program */
		Redirect redir = new Redirect(code, hooks);
		return new Rebuilder(code, funcs).rebuild(new RebuildConfig().adder(redir).lookup(redir));
	}

	public COFFObject applyHooks(Exports exports, Hooks hooks) {
		object = resolveIntrinsics(exports, hooks);
		object = applyLocalHooks(hooks);
		object = applyWin32Hooks(hooks);
		return object;
	}

	/*
	 * BTF pass 1... apply our PIC DFR, x86 pointer fixing, and getBSS fixes
	 */
	public COFFObject fixPIC(DFR resolvers, String retaddr, String getbss) {
		/* if there's nothing to do, do nothing */
		if (!resolvers.hasResolvers() && retaddr == null && getbss == null)
			return object;

		/* let's analyze (disassemble) our code first */
		Code code = Code.Init(object).analyze();

		/* get a mapping of function -> disassembled instructions */
		Map funcs = code.getCodeByFunction();

		/* Because I LOVE... safety... we're going to dangerwalk for dprintf in these helpers before we go further */
		Iterator i = resolvers.getResolverFunctions().iterator();
		while (i.hasNext()) {
			String next = (String)i.next();
			new DangerWalk(code, next).apply(funcs);
		}

		if (getbss != null)
			new DangerWalk(code, getbss).apply(funcs);

		if (retaddr != null)
			new DangerWalk(code, retaddr).apply(funcs);

		/* let's setup our pass */
		MultiModify pass = new MultiModify();

		if (resolvers.hasResolvers())
			pass.add(new ResolveAPI(code, resolvers));

		if (getbss != null)
			pass.add("x64".equals(object.getMachine()) ? new FixBSSReferencesX64(code, getbss) : new FixBSSReferencesX86(code, getbss));

		if (retaddr != null)
			pass.add(new FixX86References(code, retaddr));

		/* let's apply this pass and rebuild the program */
		return new Rebuilder(code, funcs).rebuild(new RebuildConfig().adder(pass));
	}

	/*
	 * BTF pass 2... apply the mutator, LTO, function disco, and entrypoint promotion
	 */
	public COFFObject mutate(boolean preserveFirst, ExportInfo exports, Set options) {
		/* If we're not modifying anything, then let's not do anything */
		if (options.size() == 0)
			return object;

		object = mutate_pass1(preserveFirst, exports, options);
		object = mutate_pass2(preserveFirst, exports, options);

		return object;
	}

	public COFFObject mutate_pass1(boolean preserveFirst, ExportInfo exports, Set options) {
		/* let's analyze (disassemble) our code first */
		Code code = Code.Init(object).analyze();

		/* get a mapping of function -> disassembled instructions */
		Map funcs = code.getCodeByFunction();

		/* make the go() entry point function the first one in our program */
		if (options.contains("+gofirst"))
			funcs = new GoFirst(code).apply(funcs);

		/* apply the LTO if that option was selected */
		if (options.contains("+optimize"))
			funcs = new LinkTimeOptimizer(code).apply(exports, funcs);

		/* apply the function disco, if this option was selected */
		if (options.contains("+disco"))
			funcs = new FunctionDisco(code).apply(preserveFirst, funcs);

		/* rebuild the program */
		RebuildConfig config = new RebuildConfig();

		if (options.contains("+mutate"))
			config.adder(new Mutator(code));

		if (options.contains("+regdance"))
			config.filter(new RegDance());

		return new Rebuilder(code, funcs).rebuild(config);
	}

	public COFFObject mutate_pass2(boolean preserveFirst, ExportInfo exports, Set options) {
		/* let's analyze (disassemble) our code first */
		Code code = Code.Init(object).analyze();

		/* get a mapping of function -> disassembled instructions */
		Map funcs = code.getCodeByFunction();

		/* rebuild the program */
		RebuildConfig config = new RebuildConfig();

		if (options.contains("+shatter"))
			config.filter(new Shatter());
		else if (options.contains("+blockparty"))
			config.filter(new BlockParty());

		return new Rebuilder(code, funcs).rebuild(config);
	}

	private static class MockExports implements ExportInfo {
		public MockExports() {
		}

		public Iterator iterator() {
			return new HashMap().entrySet().iterator();
		}
	}

	public static void main(String args[]) {
		if (args.length == 0) {
			CrystalUtils.print_error("./disassemble [+mutate,+optimize,+disco,+gofirst,+blockparty,+shatter] </path/to/file.o>");
			return;
		}

		COFFObject obj  = null;
		String     opts = "";

		try {
			if (args.length == 1) {
				obj  = new COFFParser().parse(CrystalUtils.readFromFile(args[0])).getObject();
			}
			else if (args.length == 2) {
				opts = args[0];
				obj  = new COFFParser().parse(CrystalUtils.readFromFile(args[1])).getObject();
			}

			/* normalize our COFF */
			COFFMerge merge = new COFFMerge();
			merge.merge(obj);
			merge.finish();
			obj = merge.getObject();

			obj = new Modify(obj).mutate(false, new MockExports(), "".equals(opts) ? new HashSet() : CrystalUtils.toSet(opts));

			CodeUtils.print(Code.Init(obj).analyze());
		}
		catch (Exception ex) {
			CrystalUtils.handleException(ex);
		}
	}
}
