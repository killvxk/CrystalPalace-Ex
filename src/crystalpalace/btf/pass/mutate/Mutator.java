package crystalpalace.btf.pass.mutate;

import crystalpalace.btf.*;
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
 * An implementation of a code mutator for Crystal Palace. While software obfuscation is a well-established
 * field, the goals are usually to protect intellectual property and frustrate reverse engineering. This
 * implementation has neither of these goals!
 *
 * The goal of this mutator is to introduce content signature resilience into the .text section of a COFF its
 * applied to0. This mutator attempts to achieve this goal by breaking up the most attractive fingerprinting
 * targets within the program. Specifically:
 *
 * - By introducing random instruction noise (e.g., variations in size, numbers of instructions) we are subtly
 *   changing (some) local jump and call targets. Function disco (+disco) helps with calls too.
 * - This mutator breaks up constants where it can (e.g., function hashing constants are attractive targets)
 * - This mutator breaks up stack strings where it can
 * - This mutator breaks up some of the CISC memory-move instructions, as these usually occur in groups and
 *   the combination of their order and various pre-baked offsets can (I hypothesize) serve as function
 *   fingerprinting tools.
 *
 * One of the ways I've measured this implementation is by looking at the longest common-substring values
 * between the original+mutated program and mutated program candidates. The idea is to reduce this value
 * downwards.
 *
 * A challenge here, and not something I've looked at yet... but I'm curious about entropy changes between
 * pre and post-mutated programs. I hypothesize entropy measures of dynamic code (e.g., non-image memory) could
 * be a sign of definitely bad vs. possibly benign. Could be.
 *
 * A lot of stuff to play with here!
 */
public class Mutator extends BaseModify {
	/* add our mutator verbs */
	public void setupVerbs() {
		verbs.add(new Load());
		verbs.add(new Store32());
		verbs.add(new Store64());
		verbs.add(new MovImmReg32());
		verbs.add(new MovImmReg64());
		verbs.add(new MovImmMem32());
		verbs.add(new Nop());
		verbs.add(new PushImm32());
		verbs.add(new ZeroReg32());
	}

	/* we're always going to punt if there's a relocation, because we don't want to deal with that here */
	public boolean shouldModify(RebuildStep step, Instruction next) {
		if (step.hasRelocation())
			return false;

		if (step.isDangerous())
			return false;

		return true;
	}

	public Mutator(Code code) {
		super(code);
	}

	public boolean isRegOnly(Instruction next) {
		InstructionInfoFactory instrInfoFactory = new InstructionInfoFactory();
		InstructionInfo info = instrInfoFactory.getInfo(next);
		return ! info.getUsedMemory().iterator().hasNext();
	}

	protected void _buildConstant64(CodeAssembler program, AsmRegister64 reg, long constant) {
		/* break up our constants! */
		long part1 = nextLong();

		/* we need a temp register for this */
		AsmRegister64 tmp = getRandReg64(reg);
		program.push(tmp);

		/* move part1 to tmp, move the difference to reg, add them together */
		program.mov(tmp, part1);
		program.mov(reg, constant - part1);
		program.add(reg, tmp);

		/* restore our random register */
		program.pop(tmp);
	}

	protected void _buildConstant(CodeAssembler program, AsmRegister32 reg, int constant) {
		/* special case */
		if (constant == 0) {
			_zeroReg32(program, reg);
			return;
		}

		/* break up our constants! */
		int part1 = nextInt(Math.abs(constant));

		switch (nextInt(4)) {
			case 0:
				_zeroReg32(program, reg);
				program.add(reg, part1);
				program.add(reg, constant - part1);
				break;
			case 1:
				program.mov(reg, part1);
				program.add(reg, constant - part1);
				break;
			case 2:
				_zeroReg32(program, reg);
				program.or(reg, part1);
				program.add(reg, constant - part1);
				break;
			case 3:
				_buildConstant(program, reg, part1);
				program.add(reg, constant - part1);
				break;
		}
	}

	protected void _nop32(CodeAssembler program) {
		AsmRegister32 reg = getRandReg32();

		switch (nextInt(6)) {
			case 0:
				program.nop();
				break;
			case 1:
				program.or(reg, reg);
				break;
			case 2:
				program.and(reg, reg);
				break;
			case 3:
				program.add(reg, 0);
				break;
			case 4:
				program.sub(reg, 0);
				break;
			case 5:
				_nop(program);
				_nop(program);
				break;
		}
	}

	protected void _nop64(CodeAssembler program) {
		AsmRegister64 reg = getRandReg64();

		switch (nextInt(6)) {
			case 0:
				program.nop();
				break;
			case 1:
				program.or(reg, reg);
				break;
			case 2:
				program.and(reg, reg);
				break;
			case 3:
				program.add(reg, 0);
				break;
			case 4:
				program.sub(reg, 0);
				break;
			case 5:
				_nop(program);
				_nop(program);
				break;
		}
	}

	protected void _nop(CodeAssembler program) {
		if ( "x86".equals(object.getMachine()) ) {
			_nop32(program);
		}
		else if ( "x64".equals(object.getMachine()) ) {
			_nop64(program);
		}
	}

	protected void _zeroReg32(CodeAssembler program, AsmRegister32 reg) {
		switch (nextInt(4)) {
			case 0:
				program.xor(reg, reg);
				break;
			case 1:
				program.and(reg, 0);
				break;
			case 2:
				program.mov(reg, 0);
				break;
			// broke one of my unit tests...
			//case 3:
			//	program.imul(reg, reg, 0);
			//	break;
			case 3:
				program.sub(reg, reg);
				break;
		}
	}

	/*
	 * Break up some constants (e.g., function hashes and such) that serve as great fingerprinting/signaturing tools
	 *
	 * We need this here, because my x86 PIC DFR uses this instruction to insert module/function hashes as args to resolver func
	 */
	private class PushImm32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "PUSH imm32".equals(istr) && next.getImmediate32() != 0 && "x86".equals(object.getMachine());
		}

		/* Replace with:
		 *
		 * push %tmp
		 * build constant in %tmp
		 * xchg %tmp, (%esp)
		 */
		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			//
			// uncomment these to check if the form we applied the transform to is always what we expect.
			//
			//CodeUtils.printInst(code, next);
			//CodeInfo.Dump(next, null);

			AsmRegister32 reg = getRandReg32();

			program.push(reg);
			_buildConstant(program, reg, next.getImmediate32());
			program.xchg(getStackPtr(), reg);
		}
	}

	/*
	 * Break up some constants (e.g., function hashes and such) that serve as great fingerprinting/signaturing tools
	 */
	private class MovImmReg32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, imm32".equals(istr) && next.getImmediate32() != 0;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			//
			// uncomment these to check if the form we applied the transform to is always what we expect.
			//
			//CodeUtils.printInst(code, next);
			//CodeInfo.Dump(next, null);

			AsmRegister32 reg = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			_buildConstant(program, reg, next.getImmediate32());
		}
	}

	private class MovImmReg64 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r64, imm64".equals(istr) && next.getImmediate64() != 0;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			//
			// uncomment these to check if the form we applied the transform to is always what we expect.
			//
			//CodeUtils.printInst(code, next);
			//CodeInfo.Dump(next, null);

			AsmRegister64 reg = new AsmRegister64( new ICRegister(next.getOp0Register()) );
			_buildConstant64(program, reg, next.getImmediate64());
		}
	}


	/*
         * We want to break these constants up, used for our stack strings on x86 and x64
	 */
	private class MovImmMem32 implements ModifyVerb {
		public boolean isDesiredReg(Instruction next) {
			return next.getMemoryBase() == Register.RBP || next.getMemoryBase() == Register.RSP || next.getMemoryBase() == Register.EBP || next.getMemoryBase() == Register.ESP;
		}

		public boolean check(String istr, Instruction next) {
			return "MOV r/m32, imm32".equals(istr) && isDesiredReg(next) && next.getMemoryIndex() == Register.NONE;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			/*
			 * We're going to replace this with...
			 * push [randomreg]
			 * [do something to build up our constant in randomreg]
			 * mov [randomreg], displ(reg)
			 * pop [randomreg]
			 */
			if ( "x64".equals(object.getMachine()) ) {
				AsmRegister64    reg64 = getRandReg64();
				AsmRegister32    reg32 = toReg32(reg64);
				AsmMemoryOperand dst   = getMemOperand(next);

				/* if we're displacing off of the stack, we need to add 8b to account for the space we created */
				if (next.getMemoryBase() == Register.RSP)
					dst = dst.add(8);

				program.push(reg64);
				_buildConstant(program, reg32, next.getImmediate32());
				program.mov(dst, reg32);
				program.pop(reg64);
			}
			else if ( "x86".equals(object.getMachine()) ) {
				AsmRegister32    reg32 = getRandReg32();
				AsmMemoryOperand dst   = getMemOperand(next);

				/* if we're displacing off of the stack, we need to add 4b to account for the space we created */
				if (next.getMemoryBase() == Register.ESP)
					dst = dst.add(4);

				program.push(reg32);
				_buildConstant(program, reg32, next.getImmediate32());
				program.mov(dst, reg32);
				program.pop(reg32);
			}
		}
	}

	/*
	 * Break-up some of the CISC memory-move instructions to create some fingerprinting resilience. These instructions often occur
	 * in groups too--making that cluster of bytes a potentially attractive fingerprinting target.
	 */
	private class Load implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r64, r/m64".equals(istr) || "MOV r32, r/m32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			String           istr  = next.getOpCode().toInstructionString();

			AsmMemoryOperand src   = getMemOperand(next);
			long             displ = next.getMemoryDisplacement64();

			/* we're not going to do anything with this */
			if (displ > 0xFF || displ < -0xFF || displ == 0 || src == null) {
				program.addInstruction(next);
			}
			/*
			 * lea rand+%src, %dst
			 * mov (rand-displ)%dst, %dst
			 */
			else if ("MOV r64, r/m64".equals(istr)) {
				AsmRegister64 dst   = new AsmRegister64( new ICRegister(next.getOp0Register()) );

				long newdispl = nextInt(10) * 8;
				if (nextInt(2) == 0)
					newdispl = newdispl * -1;

				src = src.displacement(newdispl);
				program.lea(dst, src);

				src = src.displacement(displ - newdispl).base( dst );
				program.mov(dst, src);
			}
			/*
			 * We're doing this x86 only... because trying to use our 32bit dst reg as a lea dst and
			 * base reg will go to pot on x64.
			 */
			else if ("x86".equals(object.getMachine()) && "MOV r32, r/m32".equals(istr)) {
				AsmRegister32 dst = new AsmRegister32( new ICRegister(next.getOp0Register()) );

				long newdispl = nextInt(10) * 8;
				if (nextInt(2) == 0)
					newdispl = newdispl * -1;

				src = src.displacement(newdispl);
				program.lea(dst, src);

				src = src.displacement(displ - newdispl).base( dst );
				program.mov(dst, src);
			}
			else {
				program.addInstruction(next);
			}
		}
	}

	private class Store32 implements ModifyVerb {
		public boolean isDesiredReg(Instruction next) {
			return next.getMemoryBase() == Register.EBP || next.getMemoryBase() == Register.ESP;
		}

		public boolean check(String istr, Instruction next) {
			return "MOV r/m32, r32".equals(istr) && "x86".equals(object.getMachine()) && isDesiredReg(next);
		}

		/*
		 * push %tmp
		 * lea (rand)%dst, %tmp
		 * mov %src, (displ-rand)%tmp
		 * pop %tmp
		 */
		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			String           istr  = next.getOpCode().toInstructionString();
			long             displ = next.getMemoryDisplacement64();
			AsmMemoryOperand dst   = getMemOperand(next);

			if (displ > 0xFF || displ < -0xFF || displ == 0 || dst == null) {
				program.addInstruction(next);
			}
			else {
				AsmRegister32 src = new AsmRegister32( new ICRegister(next.getOp1Register()) );
				AsmRegister32 tmp = getRandReg32(src);

				long newdispl = nextInt(10) * 8;
				if (nextInt(2) == 0)
					newdispl = newdispl * -1;

				program.push(tmp);

				dst = dst.displacement(newdispl);
				program.lea(tmp, dst);

				dst = dst.displacement(displ - newdispl).base( tmp );

				/* if we're displacing off of the stack, we need to add 4b to account for the space we created */
				if (next.getMemoryBase() == Register.ESP)
					dst = dst.add(4);

				program.mov(dst, src);

				program.pop(tmp);
			}
		}
	}

	private class Store64 implements ModifyVerb {
		public boolean isDesiredReg(Instruction next) {
			return next.getMemoryBase() == Register.RBP || next.getMemoryBase() == Register.RSP;
		}

		public boolean check(String istr, Instruction next) {
			return "MOV r/m64, r64".equals(istr) && isDesiredReg(next);
		}

		/*
		 * push %tmp
		 * lea (rand)%dst, %tmp
		 * mov %src, (displ-rand)%tmp
		 * pop %tmp
		 */
		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			String           istr  = next.getOpCode().toInstructionString();
			long             displ = next.getMemoryDisplacement64();
			AsmMemoryOperand dst   = getMemOperand(next);

			if (displ > 0xFF || displ < -0xFF || displ == 0 || dst == null) {
				program.addInstruction(next);
			}
			else {
				AsmRegister64    src = new AsmRegister64( new ICRegister(next.getOp1Register()) );
				AsmRegister64    tmp = getRandReg64(src);

				long newdispl = nextInt(10) * 8;
				if (nextInt(2) == 0)
					newdispl = newdispl * -1;

				program.push(tmp);

				dst = dst.displacement(newdispl);
				program.lea(tmp, dst);

				dst = dst.displacement(displ - newdispl).base( tmp );

				/* if we're displacing off of the stack, we need to add 4b to account for the space we created */
				if (next.getMemoryBase() == Register.RSP)
					dst = dst.add(8);

				program.mov(dst, src);

				program.pop(tmp);
			}
		}
	}

	/*
	 * Sub our nops for other nops (x86 or x64)
	 */
	private class Nop implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "NOP".equals(istr) || "NOP r/m32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			_nop(program);
		}
	}

	/*
	 * Change code to zero out a register to one of our random codes to zero out a register (x64)
	 */
	private class ZeroReg32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, imm32".equals(istr) && next.getImmediate32() == 0;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 reg = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			_zeroReg32(program, reg);
		}
	}
}
