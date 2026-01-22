package crystalpalace.btf.pass.mutate;

import crystalpalace.btf.*;
import crystalpalace.btf.lttl.*;
import crystalpalace.btf.Code;
import crystalpalace.btf.pass.*;
import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;
import java.io.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;
import com.github.icedland.iced.x86.info.*;

/* Hey, this is our chance to dynamically change the non-volatile registers */
public class RegDance implements FilterCode {
	public RegDance() {
	}

	protected Map remap = new HashMap();

	/* determine if our normalized base register is swappable with something */
	public boolean isSwappable(int x) {
		return remap.containsKey(SavedRegContext.toBaseReg(x));
	}

	/* get the right register we want to swap to, accounting for the size as well */
	public int getSwapRegister(int x) {
		AsmRegister64 reg = (AsmRegister64)remap.get( SavedRegContext.toBaseReg(x) );

		switch (SavedRegContext.toSize(x)) {
			case 64:
				return reg.get().get();
			case 32:
				return RegConvert.toReg32(reg).get().get();
			case 16:
				return RegConvert.toReg16(reg).get().get();
			case 8:
				return RegConvert.toReg8(reg).get().get();
		}

		throw new RuntimeException("invalid size for " + x);
	}

	protected String beforeAfter(int x) {
		return CodeUtils.getRegString(x) + " -> " + CodeUtils.getRegString(getSwapRegister(x));
	}

	public List filterCode(Rebuilder builder, String func, List instructions) {
		SavedRegContext stackInfo = new SavedRegContext(builder, func, instructions);
		InstructionInfo instrInfo;

		remap = new HashMap();

		/* we're not going to do anything for <= 1 registers */
		if (!stackInfo.isSane())
			return instructions;

		/* create our remapped registers */
		List swappables = new LinkedList(stackInfo.getSwappableRegisters());
		Collections.shuffle(swappables);

		Iterator orig   = stackInfo.getSwappableRegisters().iterator();
		Iterator random = swappables.iterator();
		while (orig.hasNext() && random.hasNext()) {
			remap.put(orig.next(), random.next());
		}

		//CrystalUtils.print_info(func + " with: " + stackInfo.toString(new HashSet(swappables)));

		/* go instruction by instruction */
		Iterator i = instructions.iterator();
		while (i.hasNext()) {
			Instruction next = (Instruction)i.next();
			if (stackInfo.isBookend(next)) {
				/* do nothing with the prologue and epilogue push/pops */
				continue;
			}

			/* walk our instructions, determine if a register is being used, and fix it if it is */
			for (int x = 0; x < next.getOpCount(); x++) {
				if (next.getOpKind(x) == OpKind.REGISTER && isSwappable( next.getOpRegister(x) )) {
					//CrystalUtils.print_stat("Swapping " + x + " " + beforeAfter(next.getOpRegister(x)));
					//CodeUtils.p(next);
					next.setOpRegister( x, getSwapRegister(next.getOpRegister(x)) );
					//CodeUtils.p(next);
				}
			}

			if ( isSwappable(next.getMemoryBase()) ) {
				//CrystalUtils.print_stat("Swapping BASE " + beforeAfter(next.getMemoryBase()));
				//CodeUtils.p(next);
				next.setMemoryBase( getSwapRegister(next.getMemoryBase()) );
				//CodeUtils.p(next);
			}

			if ( isSwappable(next.getMemoryIndex()) ) {
				//CrystalUtils.print_stat("Swapping INDEX " + beforeAfter(next.getMemoryIndex()));
				//CodeUtils.p(next);
				next.setMemoryIndex( getSwapRegister(next.getMemoryIndex()) );
				//CodeUtils.p(next);
			}
		}

		return instructions;
	}
}
