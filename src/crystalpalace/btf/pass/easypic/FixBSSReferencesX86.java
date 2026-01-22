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

public class FixBSSReferencesX86 extends FixBaseX86 {
	protected String getbss  = null;
	protected int    bsssize = 0;

	public boolean shouldModify(RebuildStep step, Instruction next) {
		/* we're only interested, in instructions associated with relocations */
		if (!step.hasRelocation())
			return false;

		/* we're working with .bss references */
		if ( step.getRelocation().isSection(".bss") )
			return true;

		return false;
	}

	public FixBSSReferencesX86(Code code, String getbss) {
		super(code);
		this.getbss = getbss;
	}

	public void callFixHelper(CodeAssembler program, RebuildStep step) {
		checkDanger(program, step);

		AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
		AsmRegister32 esp = new AsmRegister32(ICRegisters.esp);

		int bsslen = object.getSection(".bss").getRawData().length;

		/* save our must-preserve registers */
		List saved = pushad(program);

		/* push our bsslen as an argument onto the stack */
		program.push(bsslen);

		/* call our getbss function */
		program.call(step.getLabel(getbss));

		/* clean up the stack */
		program.add(esp, 0x4);

		/* restore our must-preserve registers */
		popad(program, saved);

		/* add our offset to %eax */
		program.add(eax, step.getRelocation().getRemoteSectionOffset());

		/* resolve the relocation, to avoid an errant write-4 somewhere else */
		step.resolve();
	}
}
