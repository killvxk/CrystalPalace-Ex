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
 * The goal of this mutator is to introduce content signature resilience into the .text section of a COFF its
 * applied to0. This mutator attempts to achieve this goal by breaking up the most attractive fingerprinting
 * targets within the program. Specifically:
 *
 * - This mutator breaks up constants where it can (e.g., function hashing constants are attractive targets)
 * - This mutator breaks up stack strings where it can
 */
public class Mutator extends BaseModify {
	/* add our mutator verbs */
	public void setupVerbs() {
		verbs.add(new Cmp());
		verbs.add(new MovImmReg32());
		verbs.add(new MovImmReg64());
		verbs.add(new MovImmMem32());
		verbs.add(new PushImm32());
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

	private class Cmp implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return ("CMP EAX, imm32".equals(istr) || "CMP r/m32, imm32".equals(istr)) && Register.isGPR32(next.getOp0Register());
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 dst   = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			AsmRegister32 tmp   = getRandReg32(dst);

			if (object.x64()) {
				AsmRegister64 tmp64 = RegConvert.toReg64(tmp);
				program.push(tmp64);
				_buildConstant(program, tmp, next.getImmediate32());
				program.cmp(dst, tmp);
				program.pop(tmp64);
			}
			else {
				program.push(tmp);
				_buildConstant(program, tmp, next.getImmediate32());
				program.cmp(dst, tmp);
				program.pop(tmp);
			}
		}
	}
}
