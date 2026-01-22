package crystalpalace.btf.lttl;

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

/*
 * What's going on here? I have an ambition, a mild one, to randomize non-volatile general-purpose registers
 * in some functions. Honestly, it's a lame-assed ambition, because in x86--I have near nothing to work with. And,
 * with x64, it's only going to be in semi-complicated functions where the number of combinations becomes remotely
 * interesting.
 *
 * The goal of this class is to parse a function prologue/epilogue to determine which non-volatile registers the
 * function is saving. And, better, to also check if RSI/DSI are being used as a general-purpose register (e.g., we
 * can swap it with others) or if it's being used in an explicit RSI/DSI-only context (e.g., a REP MOVSB aka a
 * string instruction). We do the same thing for RBX too, looking for instructions that implicitly stuff things into
 * RBX without giving us say in overriding it. I had to rely on AI to survey those situatons.
 *
 * The other thing this class does is keep track of what our push/pop prologue/epilogue functions are so we don't
 * randomize their order. If the goal is content-signature resilience, my instinct is that swapping out a known
 * common pattern that is a false positive everywhere, for something random because random is supposedly better, is
 * silly. So, I preserve the prologue/epilogue order here. We *COULD* swap the register set by updating which
 * instructions are saved, but the end result is going to be a not-too-significant multiplier on our weak-assed
 * number of combinations already.
 *
 * For an idea about reg contexts:
 *
 * 1! = 1
 * 2! = 2 combinations
 * 3! = 6			(our max for x86, wow... total pwnage!)
 * 4! = 24
 * 5! = 120
 * 6! = 720
 * 7! = 5040
 */
public class SavedRegContext {
	protected Map     prologue = new LinkedHashMap();
	protected Map     epilogue = new LinkedHashMap();
	protected Set     exclude  = new HashSet();
	protected boolean relocs   = false;

	public boolean isBookend(Instruction inst) {
		return prologue.containsKey(inst.getIP()) || epilogue.containsKey(inst.getIP());
	}

	public void dump(String name) {
		if (prologue.size() == 0)
			return;

		CrystalUtils.print_info("Stack info: " + name);
		Iterator i = prologue.values().iterator();
		while (i.hasNext()) {
			Instruction inst = (Instruction)i.next();
			CodeUtils.p(inst);
		}

		i = epilogue.values().iterator();
		while (i.hasNext()) {
			Instruction inst = (Instruction)i.next();
			CodeUtils.p(inst);
		}

		i = getSwappableRegisters().iterator();
		while (i.hasNext()) {
			CrystalUtils.print_stat( "Swappable: " + CodeUtils.getRegString(i.next()) );
		}
	}

	protected boolean isNonVolatileReg(int x) {
		return toBaseReg(x) != null;
	}

	/* this looks weird, but what I'm doing is normalizing register values to their x64 counter-part. Even in an x86
	 * program this will be OK, because we will down-convert to the right sized register when we swap */
	public static AsmRegister64 toBaseReg(int x) {
		switch (x) {
			case Register.RBX:
				return new AsmRegister64(ICRegisters.rbx);
			case Register.RDI:
				return new AsmRegister64(ICRegisters.rdi);
			case Register.RSI:
				return new AsmRegister64(ICRegisters.rsi);
			case Register.R12:
				return new AsmRegister64(ICRegisters.r12);
			case Register.R13:
				return new AsmRegister64(ICRegisters.r13);
			case Register.R14:
				return new AsmRegister64(ICRegisters.r14);
			case Register.R15:
				return new AsmRegister64(ICRegisters.r15);

			case Register.EBX:
				return new AsmRegister64(ICRegisters.rbx);
			case Register.EDI:
				return new AsmRegister64(ICRegisters.rdi);
			case Register.ESI:
				return new AsmRegister64(ICRegisters.rsi);
			case Register.R12D:
				return new AsmRegister64(ICRegisters.r12);
			case Register.R13D:
				return new AsmRegister64(ICRegisters.r13);
			case Register.R14D:
				return new AsmRegister64(ICRegisters.r14);
			case Register.R15D:
				return new AsmRegister64(ICRegisters.r15);

			case Register.BX:
				return new AsmRegister64(ICRegisters.rbx);
			case Register.DI:
				return new AsmRegister64(ICRegisters.rdi);
			case Register.SI:
				return new AsmRegister64(ICRegisters.rsi);
			case Register.R12W:
				return new AsmRegister64(ICRegisters.r12);
			case Register.R13W:
				return new AsmRegister64(ICRegisters.r13);
			case Register.R14W:
				return new AsmRegister64(ICRegisters.r14);
			case Register.R15W:
				return new AsmRegister64(ICRegisters.r15);

			case Register.BL:
				return new AsmRegister64(ICRegisters.rbx);
			case Register.BH:
				return new AsmRegister64(ICRegisters.rbx);
			case Register.DIL:
				return new AsmRegister64(ICRegisters.rdi);
			case Register.SIL:
				return new AsmRegister64(ICRegisters.rsi);
			case Register.R12L:
				return new AsmRegister64(ICRegisters.r12);
			case Register.R13L:
				return new AsmRegister64(ICRegisters.r13);
			case Register.R14L:
				return new AsmRegister64(ICRegisters.r14);
			case Register.R15L:
				return new AsmRegister64(ICRegisters.r15);
		}


		return null;
	}

	public static int toSize(int x) {
		switch (x) {
			case Register.RBX:
			case Register.RDI:
			case Register.RSI:
			case Register.R12:
			case Register.R13:
			case Register.R14:
			case Register.R15:
				return 64;

			case Register.EBX:
			case Register.EDI:
			case Register.ESI:
			case Register.R12D:
			case Register.R13D:
			case Register.R14D:
			case Register.R15D:
				return 32;

			case Register.BX:
			case Register.DI:
			case Register.SI:
			case Register.R12W:
			case Register.R13W:
			case Register.R14W:
			case Register.R15W:
				return 16;

			case Register.BH:
				throw new RuntimeException("Can't swap %bh register");
			case Register.BL:
			case Register.DIL:
			case Register.SIL:
			case Register.R12L:
			case Register.R13L:
			case Register.R14L:
			case Register.R15L:
				return 8;
		}

		throw new RuntimeException("Invalid reg: " + x);
	}

	public Set getSwappableRegisters() {
		Set temp = new HashSet();

		Iterator i = prologue.values().iterator();
		while (i.hasNext()) {
			Instruction next = (Instruction)i.next();
			if (CodeUtils.is(next, "PUSH r64")) {
				if ( isNonVolatileReg(next.getOp0Register()) )
					temp.add(toBaseReg(next.getOp0Register()));
			}
			else if (CodeUtils.is(next, "PUSH r32")) {
				if ( isNonVolatileReg(next.getOp0Register()) )
					temp.add(toBaseReg(next.getOp0Register()));
			}
		}

		temp.removeAll(exclude);

		return temp;
	}

	public Set toString(Set x) {
		Set y = new HashSet();
		Iterator i = x.iterator();
		while (i.hasNext()) {
			y.add(CodeUtils.getRegString(i.next()));
		}
		return y;
	}

	public boolean isSane() {
		return prologue.size() > 0 && prologue.size() == epilogue.size() && !relocs && getSwappableRegisters().size() >= 3;
	}

	/* let's find the prologue of our function first. */
	protected void a(ListIterator i) {
		while (i.hasNext()) {
			Instruction next = (Instruction)i.next();
			if (CodeUtils.is(next, "PUSH r64")) {
				prologue.put(next.getIP(), next);
			}
			else if (CodeUtils.is(next, "PUSH r32")) {
				prologue.put(next.getIP(), next);
			}
			else {
				break;
			}
		}

		b(i);
	}

	/* now, find the end of our function */
	protected void b(ListIterator i) {
		while (i.hasNext()) {
			Instruction next = (Instruction)i.next();
			if (CodeUtils.is(next, "RET")) {
				c(i);
				return;
			}
		}
	}

	/* walk backwards from our ret and get the epilogue */
	protected void c(ListIterator i) {
		i.previous();

		while (i.hasPrevious()) {
			Instruction prev = (Instruction)i.previous();

			if (CodeUtils.is(prev, "POP r64")) {
				epilogue.put(prev.getIP(), prev);
			}
			else if (CodeUtils.is(prev, "POP r32")) {
				epilogue.put(prev.getIP(), prev);
			}
			else {
				break;
			}
		}
	}

	protected boolean usesBH(Instruction next) {
		for (int x = 0; x < next.getOpCount(); x++) {
			if ( next.getOpKind(x) == OpKind.REGISTER && next.getOpRegister(x) == Register.BH )
				return true;
		}

		if ( next.getMemoryBase() == Register.BH )
			return true;

		if ( next.getMemoryIndex() == Register.BH )
			return true;

		return false;
	}

	protected void findSpecial(Iterator i) {
		while (i.hasNext()) {
			Instruction next = (Instruction)i.next();

			/*
			 * String instructions implicitly use %rdi and %rsi
			 */
			if (next.isStringInstruction()) {
				exclude.add(new AsmRegister64(ICRegisters.rdi));
				exclude.add(new AsmRegister64(ICRegisters.rsi));
				//CodeUtils.p(next);
			}
			/*
			 * These implicitly use %rbx
			 */
			else if (CodeUtils.is(next, "CPUID") || CodeUtils.is(next, "CMPXCHG16B") || CodeUtils.is(next, "XLAT") || CodeUtils.is(next, "CMPXCHG8B")) {
				exclude.add(new AsmRegister64(ICRegisters.rbx));
			}
			/*
			 * Don't allow %rbx if %bh is used, because our other non-volatile regs don't have a %bh equivalent
			 */
			else if (usesBH(next)) {
				exclude.add(new AsmRegister64(ICRegisters.rbx));
			}
		}
	}

	/*
	 * If a relocation uses a non-volatile register, we're not going to bother transforming the function its
	 * associated with. We *could* just exclude those registers too. But, end of the day, if we transform an
	 * instruction with a relocation by changing its registers... we risk having the relocation information drift
	 * from the expected form of the instruction. The BTF doesn't handle that well right now.
	 */
	protected boolean safeRelocs(Rebuilder builder, Iterator i) {
		while (i.hasNext()) {
			Instruction next = (Instruction)i.next();
			if (builder.getAnalysis().hasRelocation(next)) {
				for (int x = 0; x < next.getOpCount(); x++) {
                                	if ( next.getOpKind(x) == OpKind.REGISTER && isNonVolatileReg(next.getOpRegister(x)) )
						return false;
				}

				if ( isNonVolatileReg(next.getMemoryBase()) )
					return false;

				if ( isNonVolatileReg(next.getMemoryIndex()) )
					return false;
			}
		}

		return true;
	}

	public SavedRegContext(Rebuilder builder, String func, List instructions) {
		/* walk our instructions and build up an idea about the epilogue and prologue */
		a(instructions.listIterator());

		/* walk our instructions and look for situations where RBX, RSI, and RDI aren't used as
		 * general-purpose registers but are otherwise tied to a specific instruction*/
		findSpecial(instructions.iterator());

		/* look for any instructions with non-volatile registers that are also associated with a
		 * relocation. DON'T update these. Bail for this function. */
		relocs = !safeRelocs(builder, instructions.iterator());
	}
}
