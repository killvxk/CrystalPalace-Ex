package crystalpalace.btf.pass.easypic;

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

public class FixX86References extends FixBaseX86 {
	protected String      retaddr   = null;

	public boolean shouldModify(RebuildStep step, Instruction next) {
		/* we're not interested in x64 programs */
		if (x64)
			return false;

		/* we're only interested, in instructions associated with relocations */
		if (!step.hasRelocation())
			return false;

		/* parse our relocation and check for a valid import function string */
                ParseImport resolveme = new ParseImport(step.getRelocation().getSymbolName());

		/* we do NOT want to handle these import function strings... at all! */
		if (resolveme.isValid())
			return false;

		/* we don't want to handle .bss pointer fixes here. It's not appended anyways */
		if ( step.getRelocation().isSection(".bss") )
			return false;

		/* technically, I don't think this form should happen here. We either know what's in our
		 * local module (rel32) or not. This does come up though with bad function names. */
		String istr = next.getOpCode().toInstructionString();
		if ("CALL rel32".equals(istr))
			return false;

		return true;
	}

	public FixX86References(Code code, String retaddr) {
		super(code);
		this.retaddr  = retaddr;
	}

	/*
	 * Pretty easy contract
	 *
	 * [save state]
	 * call _caller
	 * mov 0x0, %tmp
	 * add 5, %tmp
	 * add %tmp, %eax
	 * [restore state]
	 */
	public void callFixHelper(CodeAssembler program, RebuildStep step) {
		checkDanger(program, step);

		AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
		AsmRegister32 ecx = new AsmRegister32(ICRegisters.ecx);

		List saved = pushad(program);

		program.call(step.getLabel(retaddr));

		step.createLabel(program);
		step.setRelocOffset(1);
		program.mov(ecx, 0);

		program.add(ecx, 5 );
		program.add(eax, ecx);

		popad(program, saved);
	}
}
