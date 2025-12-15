package crystalpalace.btf.pass;

import crystalpalace.btf.*;
import crystalpalace.btf.Code;
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

/* A base class for modifying a program with the BTF. */
public abstract class BaseModify implements AddInstruction {
	protected Code                code     = null;
	protected COFFObject          object   = null;
	protected List                verbs    = new LinkedList();
	protected boolean             x64      = false;
	protected Random              rng      = new Random();

	public int nextInt(int max) {
		/* I get this from unhook.x64.o and Math.abs will return a negative value on this value */
		if (max == Integer.MIN_VALUE)
			return rng.nextInt();

		return rng.nextInt(max);
	}

	public long nextLong() {
		return rng.nextLong();
	}

	/* setup our modification verbs */
	public abstract void setupVerbs();

	/* a first pass to determine whether or not we even want to touch the instruction */
	public abstract boolean shouldModify(RebuildStep step, Instruction next);

	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		/* this is our fallback, just add the instruction if nothing applies */
		program.addInstruction(next);
	}

	public BaseModify(Code code) {
		this.code     = code;
		this.object   = code.getObject();
		this.x64      = "x64".equals( object.getMachine() );

		try {
			rng = java.security.SecureRandom.getInstanceStrong();
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}

		setupVerbs();
	}

	public interface ModifyVerb {
		public boolean check(String istr, Instruction next);
		public void apply(CodeAssembler program, RebuildStep step, Instruction next);
	}

	public void modify(CodeAssembler program, RebuildStep step, Instruction next) {
		/* convert our instruction to a string, so we check it against our mutators */
		String istr = next.getOpCode().toInstructionString();

		/* walk our various resolve verbs, find one that wants to apply and do it */
		Iterator i = verbs.iterator();
		while (i.hasNext()) {
			ModifyVerb verb = (ModifyVerb)i.next();
			if (verb.check(istr, next)) {
				verb.apply(program, step, next);
				return;
			}
		}

//		System.out.println(istr);
//		CodeUtils.printInst(code, next);
//		CodeInfo.Dump(next, null);
		noMatch(program, step, next);
	}

	public void addInstruction(CodeAssembler program, RebuildStep step, Instruction next) {
		/* our pre-amble, do we want to modify or not? */
		if (!shouldModify(step, next)) {
			program.addInstruction(next);
			return;
		}

		/* safety check */
		if (step.isDangerous()) {
			if (step.hasRelocation())
				throw new RuntimeException(this.getClass().getSimpleName() + " can't instrument " + step.getInstructionString() + " for " + step.getRelocation() + ": we might corrupt EFLAGS/RFLAGS");
			else
				throw new RuntimeException(this.getClass().getSimpleName() + " can't instrument " + step.getInstructionString() + " in " + step.getFunction() + ": we might corrupt EFLAGS/RFLAGS");
		}

		/* break this up further... */
		modify(program, step, next);
	}

	public AsmRegister8 toReg8(AsmRegister64 reg) {
		if (reg.get() == ICRegisters.rax)
			return new AsmRegister8(ICRegisters.al);

		else if (reg.get() == ICRegisters.rbx)
			return new AsmRegister8(ICRegisters.bl);

		else if (reg.get() == ICRegisters.rcx)
			return new AsmRegister8(ICRegisters.cl);

		else if (reg.get() == ICRegisters.rdi)
			return new AsmRegister8(ICRegisters.dil);

		else if (reg.get() == ICRegisters.rdx)
			return new AsmRegister8(ICRegisters.dl);

		else if (reg.get() == ICRegisters.rsi)
			return new AsmRegister8(ICRegisters.sil);

		else if (reg.get() == ICRegisters.r8)
			return new AsmRegister8(ICRegisters.r8b);

		else if (reg.get() == ICRegisters.r9)
			return new AsmRegister8(ICRegisters.r9b);

		else if (reg.get() == ICRegisters.r10)
			return new AsmRegister8(ICRegisters.r10b);

		else if (reg.get() == ICRegisters.r11)
			return new AsmRegister8(ICRegisters.r11b);

		else if (reg.get() == ICRegisters.r12)
			return new AsmRegister8(ICRegisters.r12b);

		else if (reg.get() == ICRegisters.r13)
			return new AsmRegister8(ICRegisters.r13b);

		else if (reg.get() == ICRegisters.r14)
			return new AsmRegister8(ICRegisters.r14b);

		else if (reg.get() == ICRegisters.r15)
			return new AsmRegister8(ICRegisters.r15b);

		return null;
	}

	public AsmRegister16 toReg16(AsmRegister64 reg) {
		if (reg.get() == ICRegisters.rax)
			return new AsmRegister16(ICRegisters.ax);

		else if (reg.get() == ICRegisters.rbx)
			return new AsmRegister16(ICRegisters.bx);

		else if (reg.get() == ICRegisters.rcx)
			return new AsmRegister16(ICRegisters.cx);

		else if (reg.get() == ICRegisters.rdi)
			return new AsmRegister16(ICRegisters.di);

		else if (reg.get() == ICRegisters.rdx)
			return new AsmRegister16(ICRegisters.dx);

		else if (reg.get() == ICRegisters.rsi)
			return new AsmRegister16(ICRegisters.si);

		else if (reg.get() == ICRegisters.r8)
			return new AsmRegister16(ICRegisters.r8w);

		else if (reg.get() == ICRegisters.r9)
			return new AsmRegister16(ICRegisters.r9w);

		else if (reg.get() == ICRegisters.r10)
			return new AsmRegister16(ICRegisters.r10w);

		else if (reg.get() == ICRegisters.r11)
			return new AsmRegister16(ICRegisters.r11w);

		else if (reg.get() == ICRegisters.r12)
			return new AsmRegister16(ICRegisters.r12w);

		else if (reg.get() == ICRegisters.r13)
			return new AsmRegister16(ICRegisters.r13w);

		else if (reg.get() == ICRegisters.r14)
			return new AsmRegister16(ICRegisters.r14w);

		else if (reg.get() == ICRegisters.r15)
			return new AsmRegister16(ICRegisters.r15w);

		return null;
	}

	public AsmRegister32 toReg32(AsmRegister64 reg) {
		if (reg.get() == ICRegisters.rax)
			return new AsmRegister32(ICRegisters.eax);

		else if (reg.get() == ICRegisters.rbx)
			return new AsmRegister32(ICRegisters.ebx);

		else if (reg.get() == ICRegisters.rcx)
			return new AsmRegister32(ICRegisters.ecx);

		else if (reg.get() == ICRegisters.rdi)
			return new AsmRegister32(ICRegisters.edi);

		else if (reg.get() == ICRegisters.rdx)
			return new AsmRegister32(ICRegisters.edx);

		else if (reg.get() == ICRegisters.rsi)
			return new AsmRegister32(ICRegisters.esi);

		else if (reg.get() == ICRegisters.r8)
			return new AsmRegister32(ICRegisters.r8d);

		else if (reg.get() == ICRegisters.r9)
			return new AsmRegister32(ICRegisters.r9d);

		else if (reg.get() == ICRegisters.r10)
			return new AsmRegister32(ICRegisters.r10d);

		else if (reg.get() == ICRegisters.r11)
			return new AsmRegister32(ICRegisters.r11d);

		else if (reg.get() == ICRegisters.r12)
			return new AsmRegister32(ICRegisters.r12d);

		else if (reg.get() == ICRegisters.r13)
			return new AsmRegister32(ICRegisters.r13d);

		else if (reg.get() == ICRegisters.r14)
			return new AsmRegister32(ICRegisters.r14d);

		else if (reg.get() == ICRegisters.r15)
			return new AsmRegister32(ICRegisters.r15d);

		return null;
	}

	public AsmRegister64 getRandReg64(AsmRegister64 exclude) {
		while (true) {
			AsmRegister64 cand = getRandReg64();
			if (!cand.equals(exclude))
				return cand;
		}
	}

	public AsmRegister64 getRandReg64() {
		switch (nextInt(14)) {
			case 0:
				return new AsmRegister64(ICRegisters.rax);
			case 1:
				return new AsmRegister64(ICRegisters.rbx);
			case 2:
				return new AsmRegister64(ICRegisters.rcx);
			case 3:
				return new AsmRegister64(ICRegisters.rdi);
			case 4:
				return new AsmRegister64(ICRegisters.rdx);
			case 5:
				return new AsmRegister64(ICRegisters.rsi);
			case 6:
				return new AsmRegister64(ICRegisters.r8);
			case 7:
				return new AsmRegister64(ICRegisters.r9);
			case 8:
				return new AsmRegister64(ICRegisters.r10);
			case 9:
				return new AsmRegister64(ICRegisters.r11);
			case 10:
				return new AsmRegister64(ICRegisters.r12);
			case 11:
				return new AsmRegister64(ICRegisters.r13);
			case 12:
				return new AsmRegister64(ICRegisters.r14);
			case 13:
				return new AsmRegister64(ICRegisters.r15);
		}

		throw new RuntimeException("Missing case.");
	}

	public AsmMemoryOperand getStackPtr() {
		if (x64) {
			return AsmRegisters.mem_ptr(new AsmRegister64(ICRegisters.rsp), 0);
		}
		else {
			return AsmRegisters.mem_ptr(new AsmRegister32(ICRegisters.esp), 0);
		}
	}

	public List pushad(CodeAssembler program) {
		return pushad(program, null);
	}

	public List pushad(CodeAssembler program, Object exclude) {
		/* build up our list of registers we need to save */
		List regs = new LinkedList();

		if (x64) {
			regs.add(new AsmRegister64(ICRegisters.rcx));
			regs.add(new AsmRegister64(ICRegisters.rdx));
			regs.add(new AsmRegister64(ICRegisters.r8));
			regs.add(new AsmRegister64(ICRegisters.r9));
			regs.add(new AsmRegister64(ICRegisters.r10));
			regs.add(new AsmRegister64(ICRegisters.r11));
		}
		else {
			regs.add(new AsmRegister32(ICRegisters.ecx));
			regs.add(new AsmRegister32(ICRegisters.edx));
		}

		/* exclude any registers */
		if (exclude != null) {
			regs.remove(exclude);
		}

		/* shuffle their order */
		Collections.shuffle(regs);

		/* push them to the stack */
		Iterator i = regs.iterator();
		while (i.hasNext()) {
			if (x64) {
				AsmRegister64 temp = (AsmRegister64)i.next();
				program.push(temp);
			}
			else {
				AsmRegister32 temp = (AsmRegister32)i.next();
				program.push(temp);
			}
		}

		return regs;
	}

	public void stackAlloc(CodeAssembler program, int x) {
		AsmRegister64 rsp = new AsmRegister64(ICRegisters.rsp);
		program.sub(rsp, x);
	}

	public void stackDealloc(CodeAssembler program, int x) {
		AsmRegister64 rsp = new AsmRegister64(ICRegisters.rsp);
		program.add(rsp, x);
	}

	public void popad(CodeAssembler program, List regs) {
		/* reverse our regs list */
		Collections.reverse(regs);

		/* pop each reg off the stack */
		Iterator i = regs.iterator();
		while (i.hasNext()) {
			if (x64) {
				AsmRegister64 temp = (AsmRegister64)i.next();
				program.pop(temp);
			}
			else {
				AsmRegister32 temp = (AsmRegister32)i.next();
				program.pop(temp);
			}
		}
	}

	public AsmMemoryOperand getMemOperand(Instruction next) {
		if (next.getMemoryBase() == Register.NONE)
			return null;

		AsmMemoryOperand   cand = null;

		if (next.getMemoryIndex() == Register.NONE) {
			if ("x64".equals(object.getMachine()))
				cand = AsmRegisters.mem_ptr(new AsmRegister64(new ICRegister(next.getMemoryBase())), next.getMemoryDisplacement64());
			else
				cand = AsmRegisters.mem_ptr(new AsmRegister32(new ICRegister(next.getMemoryBase())), next.getMemoryDisplacement32());
		}
		else if (next.getMemoryIndex() != Register.NONE) {
			/*
			 * This invalidates the "else" logic, below, to create an operand that accounts for the index register and scale reg. I'm keeping the
			 * old code (below) in place for reference (in case I want to ressurect this later).
			 *
			 * My transforms that work with memory operands and use random registers to help do not have logic to exclude the index registers.
			 * This is a crash/corruption waiting to happen and needs to be resolved before I transform instructions with index registers.
			 *
			 * And, in my testing with -O enabled, I'm seeing some pointer corruption and weird crashes when I transform code with an index
			 * register. It COULD be this random register generation issue or something else. Either way, taking this issue out of play for now.
			 */
			return null;
		}
		else {
			if ("x64".equals(object.getMachine())) {
				cand = AsmRegisters.mem_ptr(
					new AsmRegister64(new ICRegister(next.getMemoryBase())),
					new AsmRegister64(new ICRegister(next.getMemoryIndex())),
					next.getMemoryIndexScale(),
					next.getMemoryDisplacement64());
			}
			else {
				cand = AsmRegisters.mem_ptr(
					new AsmRegister32(new ICRegister(next.getMemoryBase())),
					new AsmRegister32(new ICRegister(next.getMemoryIndex())),
					next.getMemoryIndexScale(),
					next.getMemoryDisplacement32());
			}
		}

		if (next.getMemorySegment() == Register.GS || next.getMemorySegment() == Register.FS) {
			AsmRegisterSegment seg = new AsmRegisterSegment(new ICRegister(next.getMemorySegment()));
			return cand.segment(seg);
		}
		else {
			return cand;
		}
	}

	public AsmRegister32 getRandReg32(AsmRegister32 exclude) {
		while (true) {
			AsmRegister32 cand = getRandReg32();
			if (!cand.equals(exclude))
				return cand;
		}
	}

	public AsmRegister32 getReg32(List saved, AsmRegister32 exclude) {
		if (exclude == null)
			return (AsmRegister32)saved.get(0);

		Iterator i = saved.iterator();
		while (i.hasNext()) {
			AsmRegister32 reg = (AsmRegister32)i.next();
			if (!reg.equals(exclude))
				return reg;

			//CrystalUtils.print_error("Saved a crash. Skipped dst: " + exclude);
		}

		return null;
	}

	public AsmRegister32 getRandReg32() {
		switch (nextInt(6)) {
			case 0:
				return new AsmRegister32(ICRegisters.eax);
			case 1:
				return new AsmRegister32(ICRegisters.ebx);
			case 2:
				return new AsmRegister32(ICRegisters.ecx);
			case 3:
				return new AsmRegister32(ICRegisters.edi);
			case 4:
				return new AsmRegister32(ICRegisters.edx);
			case 5:
				return new AsmRegister32(ICRegisters.esi);
		}

		throw new RuntimeException("Missing case.");
	}
}
