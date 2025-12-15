package crystalpalace.btf.pass.hook;

import crystalpalace.btf.*;
import crystalpalace.btf.Code;
import crystalpalace.btf.pass.*;
import crystalpalace.coff.*;
import crystalpalace.util.*;
import crystalpalace.export.*;

import java.util.*;
import java.io.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;
import com.github.icedland.iced.x86.info.*;

/* handle our linker intrinsic for resolving hooks */
public class ResolveHooks extends BaseModify {
	protected Hooks hooks     = null;

	public void setupVerbs() {
		verbs.add(new Call32());
	}

	public boolean shouldModify(RebuildStep step, Instruction next) {
		/* we're only interested, in instructions associated with relocations */
		if (!step.hasRelocation())
			return false;

		if (x64)
			return"__resolve_hook".equals(step.getRelocation().getSymbolName());
		else
			return"___resolve_hook".equals(step.getRelocation().getSymbolName());
	}

	/* fail if we can't resolve something */
	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		throw new RuntimeException("Can't expand linker intrinsic __resolve_hook() for " + step.getInstructionString());
	}

	public ResolveHooks(Code code, Hooks hooks) {
		super(code);
		this.hooks = hooks;
	}

	public void generateResolver_x86(CodeAssembler program, RebuildStep step) {
		AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
		AsmRegister32 esp = new AsmRegister32(ICRegisters.esp);

		/* create our done CodeLabel */
		CodeLabel done = program.createLabel();

		/* get our first argument off of the stack */
		program.mov(eax, AsmRegisters.mem_ptr(new AsmRegister32(ICRegisters.esp), 0));

		/* walk our set of various IAT hooks that the user registered */
		Iterator i = hooks.getResolveHooks().iterator();
		while (i.hasNext()) {
			Hooks.ResolveHook reshook  = (Hooks.ResolveHook)i.next();
			Symbol            wrapper  = null;
			String            hookfunc = null;

			/* determine which hook to use, an explicit one or one attached to the API */
			if (reshook.isSelf()) {
				hookfunc = hooks.getHook(step.getFunction(), reshook.getTarget());
			}
			else {
				hookfunc = reshook.getWrapper();
			}

			/* get all of it, I guess */
			wrapper = object.getSymbol(hookfunc);
			if (wrapper == null)
				continue;

			/*
			 * cmp [ror13 hash], %eax
			 * jne next
			 * mov 0, %eax
			 * 	`- relocation for wrapper symbol here!
			 * jmp done
			 * next:
			 */
			program.cmp(eax, reshook.getFunctionHash());
			program.jne(program.f());
			step.createRelocationFor(program, wrapper, 1);
			program.mov(eax,  0);
			program.jmp(done);

			program.anonymousLabel();
		}

		/* our default result, which is to put 0 in %eax */
		program.xor(eax, eax);

		/* this is the exit point for the whole thing */
		program.label(done);
	}

	public void generateResolver_x64(CodeAssembler program, RebuildStep step) {
		AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);
		AsmRegister32 ecx = new AsmRegister32(ICRegisters.ecx);

		/* create our done CodeLabel */
		CodeLabel done = program.createLabel();

		/* walk our set of various IAT hooks that the user registered */
		Iterator i = hooks.getResolveHooks().iterator();
		while (i.hasNext()) {
			Hooks.ResolveHook reshook  = (Hooks.ResolveHook)i.next();
			Symbol            wrapper  = null;
			String            hookfunc = null;

			/* determine which hook to use, an explicit one or one attached to the API */
			if (reshook.isSelf()) {
				hookfunc = hooks.getHook(step.getFunction(), reshook.getTarget());
			}
			else {
				hookfunc = reshook.getWrapper();
			}

			/* get all of it, I guess */
			wrapper = object.getSymbol(hookfunc);
			if (wrapper == null)
				continue;

			/*
			 * cmp [ror13 hash], %rcx
			 * jne next
			 * lea [label to wrapper], %rax
			 * jmp done
			 * next:
			 */
			program.cmp(ecx, reshook.getFunctionHash());
			program.jne(program.f());
			program.lea( rax, AsmRegisters.mem_ptr(step.getLabel(hookfunc)) );
			program.jmp(done);

			program.anonymousLabel();
		}

		/* our default result, which is to put 0 in %eax */
		program.xor(rax, rax);

		/* this is the exit point for the whole thing */
		program.label(done);
	}

	private class Call32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CALL rel32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			if (x64)
				generateResolver_x64(program, step);
			else
				generateResolver_x86(program, step);
			step.resolve();
		}
	}
}
