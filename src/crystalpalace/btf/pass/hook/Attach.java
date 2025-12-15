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

/* This is kind of a cool set of transforms, we're looking for instructions that have a relocation for
 * MODULE$Function and we're rewriting the program to call a DFR function (in the program) to resolve
 * that relocation using the ROR-hash of the module and function as args. This makes it a lot easier to
 * write PIC, because calling Win32 APIs is now seamless and arguably, framework-less. */
public class Attach extends BaseModify {
	protected Hooks       hooks     = null;
	protected ParseImport resolveme = null;
	protected String      hookfunc  = null;

	public void setupVerbs() {
		if (x64) {
			verbs.add(new MovRax());
			verbs.add(new Call64());
		}
		else {
			verbs.add(new MovEax());
			verbs.add(new Call32());
		}
	}

	public boolean shouldModify(RebuildStep step, Instruction next) {
		/* we're only interested, in instructions associated with relocations */
		if (!step.hasRelocation())
			return false;

		/* parse our relocation and check for a valid import function string */
		resolveme = new ParseImport(step.getRelocation().getSymbolName());

		if (!resolveme.isValid())
			return false;

		/* check that our import is in MODULE$Function format or its GetProcAddress/LoadLibraryA */
		resolveme.checkAndPopulateModule();

		/* try to resolve a hook for this context */
		hookfunc = hooks.getHook(step.getFunction(), resolveme.getTarget());

		if (hookfunc == null)
			return false;

		return true;
	}

	/* fail if we can't resolve something */
	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		System.out.println(next.getOpCode().toInstructionString());
		CodeUtils.printInst(code, next);
		CodeInfo.Dump(next, null);

		throw new RuntimeException("Can't transform '" + step.getInstructionString() + "' to attach to API " + step.getRelocation() + ". Change your compiler optimization settings or turn off optimizations for the caller function.");
	}

	public Attach(Code code, Hooks hooks) {
		super(code);
		this.hooks = hooks;
	}

	private class Call64 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CALL r/m64".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			program.call(step.getLabel(hookfunc));
			step.resolve();
		}
	}

	private class Call32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CALL r/m32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			program.call(step.getLabel(hookfunc));
			step.resolve();
		}
	}

	private class MovEax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV EAX, moffs32".equals(istr);
		}

		public boolean isCallEaxNext(RebuildStep step) {
			Instruction peek = step.peekNext();

			if (peek == null)
				return false;

			return "CALL r/m32".equals(peek.getOpCode().toInstructionString()) && peek.getOp0Register() == Register.EAX;
		}


		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			if (isCallEaxNext(step)) {
				step.consumeNext(); /* eat the next instruction, because we're going to replace it too */
				program.call( step.getLabel(hookfunc) );
				step.resolve(); /* swallow our relocation too */
			}
			else {
				/* get our hook function symbol */
				Symbol temp = object.getSymbol(hookfunc);

				/* move the constant for our hookfunc into %eax */
				program.mov(new AsmRegister32(new ICRegister(next.getOp0Register())), 0);

				/* change the relocation symbol to .text, please */
				step.getRelocation().setSymbolName(".text");

				/* update the offset [from symbol] value stored with our relocation to be our hookfunc */
				step.setRelocOffsetValue(temp.getValue());

				/* The above works because our Rebuilder has a special case to fix .text offsets */
			}
		}
	}

	private class MovRax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r64, r/m64".equals(istr) && next.getOp0Register() == Register.RAX;
		}

		public boolean isCallRaxNext(RebuildStep step) {
			Instruction peek = step.peekNext();

			if (peek == null)
				return false;

			return "CALL r/m64".equals(peek.getOpCode().toInstructionString()) && peek.getOp0Register() == Register.RAX;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			if (isCallRaxNext(step)) {
				step.consumeNext(); /* eat the next instruction, because we're going to replace it too */
				program.call( step.getLabel(hookfunc) );
			}
			else {
				program.lea( new AsmRegister64(new ICRegister(next.getOp0Register())), AsmRegisters.mem_ptr(  step.getLabel(hookfunc)  ));
			}

			/* swallow our relocation too */
			step.resolve();
		}
	}
}
