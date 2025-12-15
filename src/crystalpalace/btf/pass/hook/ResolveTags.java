package crystalpalace.btf.pass.hook;

import crystalpalace.btf.*;
import crystalpalace.btf.Code;
import crystalpalace.btf.pass.*;
import crystalpalace.coff.*;
import crystalpalace.util.*;
import crystalpalace.export.*;

import crystalpalace.spec.TagStore;

import java.util.*;
import java.io.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;
import com.github.icedland.iced.x86.info.*;

/* handle our linker intrinsic for resolving __tag_SYMBOL() */
public class ResolveTags extends BaseModify {
	protected TagStore     tags;
	protected TagStore.Tag tag = null;

	public void setupVerbs() {
		verbs.add(new Call32());
	}

	public boolean shouldModify(RebuildStep step, Instruction next) {
		/* we're only interested, in instructions associated with relocations */
		if (!step.hasRelocation())
			return false;

		String symbol = step.getRelocation().getSymbolName();

		tag = tags.getSymbolTag(symbol);
		if (tag != null)
			return true;

		/* throw an error if this matches, but doesn't have a tag defined */
		if (symbol.startsWith("__tag_") || symbol.startsWith("___tag_"))
			throw new RuntimeException("exporfunc for " + symbol + " not found at " + step.getInstructionString());

		return false;
	}

	/* fail if we can't resolve something */
	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		throw new RuntimeException("Can't expand linker intrinsic " + tag.getSymbol() + " for " + step.getInstructionString());
	}

	public ResolveTags(Code code, Exports exports) {
		super(code);
		this.tags = exports.getTags();
	}

	private class Call32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CALL rel32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			if (x64) {
				AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);
				program.mov(rax, tag.getTag());
			}
			else {
				AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
				program.mov(eax, tag.getTag());
			}
			step.resolve();
		}
	}
}
