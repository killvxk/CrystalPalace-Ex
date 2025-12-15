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

public class Redirect extends BaseModify implements ResolveLabel {
	protected Hooks       hooks     = null;
	protected String      hookfunc  = null;

	public Redirect(Code code, Hooks hooks) {
		super(code);
		this.hooks = hooks;
	}

	public CodeLabel getCodeLabel(RebuildStep step, String symbol) {
		hookfunc = hooks.getLocalHook(step.getFunction(), symbol);

		if (hookfunc != null)
			return step.getLabel(hookfunc);

		return step.getLabel(symbol);
	}

	public void setupVerbs() {
		if (!x64) {
			verbs.add(new LoadAddress());
		}
	}

	public boolean shouldModify(RebuildStep step, Instruction next) {
		/* we're only interested, in instructions associated with relocations */
		if (!step.hasRelocation())
			return false;

		/* check if the relocation is within .text */
		if (! ".text".equals(step.getRelocation().getSymbolName()) )
			return false;

		/* get a symbol for our thing */
		Symbol temp = code.getLabel(step.getRelocOffsetValue());
		if (temp == null)
			return false;

		/* try to resolve a hook for this context */
		hookfunc = hooks.getLocalHook(step.getFunction(), temp.getName());

		if (hookfunc == null)
			return false;

		return true;
	}

	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		System.out.println(next.getOpCode().toInstructionString());
		CodeUtils.printInst(code, next);
		CodeInfo.Dump(next, null);

		throw new RuntimeException("Can't transform '" + step.getInstructionString() + "' to redirect to " + hookfunc + ". I don't have logic for this instruction form.");
	}

	private class LoadAddress implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r/m32, imm32".equals(istr) || "MOV r32, imm32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			/* get our hook function symbol */
			Symbol temp = object.getSymbol(hookfunc);

			/* add our instruction */
			program.addInstruction(next);

			/* update the offset [from symbol] value stored with our relocation to be our hookfunc */
			step.setRelocOffsetValue(temp.getValue());
		}
	}
}
