package crystalpalace.btf.lttl;

import crystalpalace.btf.*;
import crystalpalace.btf.Code;
import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;
import com.github.icedland.iced.x86.fmt.fast.*;
import com.github.icedland.iced.x86.info.*;

/*
 * Pass-through of our various rebuild interfaces.
 */
public class PassThrough implements AddInstruction, ResolveLabel, FilterCode {
	public PassThrough() {
	}

	/* default implementation of code to resolve the right label for a local symbol */
	public CodeLabel getCodeLabel(RebuildStep step, String symbol) {
		return step.getLabel(symbol);
	}

	/* default implementation for GetCodeIterator */
	public void analyze(Rebuilder builder, Map funcs) {
	}

	/* default implementation of code to walk through a function... block by block */
	public List filterCode(Rebuilder builder, String func, List instructions) {
		return instructions;
	}

	/* default implementation of code to add a mutated/modified individual instruction to the mix */
	public void addInstruction(CodeAssembler program, RebuildStep step, Instruction copy) {
		program.addInstruction(copy);
	}
}
