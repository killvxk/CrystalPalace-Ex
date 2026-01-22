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

public abstract class FixBaseX86 extends BaseModify {
	public void setupVerbs() {
		/* add EAX */
		verbs.add(new AddEax());

		/* call r/m32 */
		verbs.add(new Call32());

		/* cmp EAX */
		verbs.add(new CmpEax());
		verbs.add(new CompareConstant());

		/* our load address ops */
		verbs.add(new LoadAddress());
		verbs.add(new LoadAddressLEA());
		verbs.add(new LoadAddressReg());

		/* load value ops */
		verbs.add(new LoadEAX());
		verbs.add(new Load32_8_S());
		verbs.add(new Load32_8_Z());
		verbs.add(new Load32_16_S());
		verbs.add(new Load32_16_Z());
		verbs.add(new Load32_32());

		/* store value ops */
		verbs.add(new Store32_32());
		verbs.add(new Store32_16());
		verbs.add(new Store32_8());

		/* store constant ops */
		verbs.add(new StoreConstant());
	}

	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		CodeUtils.p(step);
		CodeInfo.Dump(next, null);

		throw new RuntimeException(this.getClass().getSimpleName() + ": Can't transform '" + step.getInstructionString() + "' to handle " + step.getRelocation() + ". Change your compiler optimization settings or turn off optimizations for " + step.getFunction());
	}

	public FixBaseX86(Code code) {
		super(code);
	}

	/* This is our catch-all to look for eflags modification and warn appropriately about it */
	protected void checkDanger(CodeAssembler program, RebuildStep step) {
		if (!step.isDangerous())
			return;

		CodeUtils.p(step);
		throw new RuntimeException(this.getClass().getSimpleName() + ": Possible eflags corruption if I transform '" + step.getInstructionString() +"' to handle " + step.getRelocation() + ". Change your compiler optimization settings or turn off optimizations for " + step.getFunction());
	}

	public abstract void callFixHelper(CodeAssembler program, RebuildStep step);

	private class AddEax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "ADD EAX, imm32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 ecx = new AsmRegister32(ICRegisters.ecx);

			program.push(ecx);
				program.push(eax);
					callFixHelper(program, step);
					program.mov(ecx, eax);
				program.pop(eax);
				program.add(eax, ecx);
			program.pop(ecx);
		}
		// 2026.01.06 - unit test 43
	}

	private class Call32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CALL r/m32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

			/* get our BSS pointer */
			callFixHelper(program, step);

			/* %eax <- [%eax] */
			program.mov(eax, AsmRegisters.mem_ptr(eax, 0));

			/* call %eax */
			program.call(eax);
		}
		// 2026.01.03 - represented in stack cutting (-O1) TCG example
	}

	private class CompareConstant implements ModifyVerb {
		protected Set valid = new HashSet();

		public CompareConstant() {
			valid.add("CMP r/m8, imm8");
			valid.add("CMP r/m16, imm16");
			valid.add("CMP r/m32, imm32");
		}

		public boolean check(String istr, Instruction next) {
			return valid.contains(istr) && next.getMemoryBase() == Register.NONE;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			int           val  = next.getImmediate32();
			String        istr = next.getOpCode().toInstructionString();
			AsmRegister32 eax  = new AsmRegister32(ICRegisters.eax);

			/* push our %eax register */
			program.push(eax);

			/* let's get our .bss pointer */
			callFixHelper(program, step);

			/* [eax] <- val ; type hint (e.g., byte_ptr) dictates the instruction that gets generated */
			if ("CMP r/m8, imm8".equals(istr)) {
				program.cmp(AsmRegisters.byte_ptr(eax, 0), val);
			}
			else if ("CMP r/m16, imm16".equals(istr)) {
				program.cmp(AsmRegisters.word_ptr(eax, 0), val);
			}
			else if ("CMP r/m32, imm32".equals(istr)) {
				program.cmp(AsmRegisters.dword_ptr(eax, 0), val);
			}
			else {
				throw new RuntimeException("Invalid istr " + istr + " in x86 CompareConstant");
			}

			/* bring %rax back */
			program.pop(eax);
		}
		// 2026.01.03 - represented in unit tests.
	}

	private class CmpEax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CMP EAX, imm32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 ecx = new AsmRegister32(ICRegisters.ecx);

			program.push(ecx);
				program.push(eax);
					callFixHelper(program, step);
					program.mov(ecx, eax);
				program.pop(eax);
				program.cmp(eax, ecx);
			program.pop(ecx);
		}
		// 2016.01.03 - represented in unit tests.
	}

	private class StoreConstant implements ModifyVerb {
		protected Set valid = new HashSet();

		public StoreConstant() {
			valid.add("MOV r/m8, imm8");
			valid.add("MOV r/m16, imm16");
			valid.add("MOV r/m32, imm32");
		}

		public boolean check(String istr, Instruction next) {
			return valid.contains(istr) && next.getMemoryBase() == Register.NONE;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			int           val  = next.getImmediate32();
			String        istr = next.getOpCode().toInstructionString();
			AsmRegister32 eax  = new AsmRegister32(ICRegisters.eax);

			/* validating that the relocation is for the address/where to store the constant */
			if (!step.isRelocDisplacement())
				noMatch(program, step, next);

			/* push our %eax register */
			program.push(eax);

			/* let's get our .bss pointer */
			callFixHelper(program, step);

			/* [eax] <- val ; type hint (e.g., byte_ptr) dictates the instruction that gets generated */
			if ("MOV r/m8, imm8".equals(istr)) {
				program.mov(AsmRegisters.byte_ptr(eax, 0), val);
			}
			else if ("MOV r/m16, imm16".equals(istr)) {
				program.mov(AsmRegisters.word_ptr(eax, 0), val);
			}
			else if ("MOV r/m32, imm32".equals(istr)) {
				program.mov(AsmRegisters.dword_ptr(eax, 0), val);
			}
			else {
				throw new RuntimeException("Invalid istr " + istr + " in x86 StoreConstant");
			}

			/* bring %rax back */
			program.pop(eax);
		}
		// 2026.01.03 - represented in unit tests.
	}

	private abstract class LoadValue32 implements ModifyVerb {
		protected abstract void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src);

		public boolean isGood(Instruction next) {
			if (next.getMemoryBase() == Register.NONE)
				return true;

			if (next.getMemoryBase() == Register.ESP || next.getMemoryBase() == Register.EBP)
				return false;

			return next.getMemoryIndex() == Register.NONE;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 dst  = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			AsmRegister32 eax  = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 esp  = new AsmRegister32(ICRegisters.esp);

			if (next.getMemoryBase() != Register.NONE) {
				AsmRegister32 base = new AsmRegister32( new ICRegister(next.getMemoryBase()) );
				program.sub(esp, 4);

				/* store our base register at top slot in the stack */
				program.mov(getStackPtr(), base);

				// A
				if (dst.equals(eax)) {
					/* get our BSS pointer */
					callFixHelper(program, step);

					/* add base register value to eax */
					program.add(eax, getStackPtr(0));

					/* %eax <- [%eax] */
					load(program, next, eax, eax);
				}
				// B
				else {
					/* save our eax register */
					program.push(eax);

					/* get our BSS pointer */
					callFixHelper(program, step);

					/* add base register value to eax */
					program.add(eax, getStackPtr(4));

					/* %dst <- [%eax] */
					load(program, next, dst, eax);

					/* restore our eax register */
					program.pop(eax);
				}

				program.add(esp, 4);
			}
			else {
				// C
				if (dst.equals(eax)) {
					/* get our BSS pointer */
					callFixHelper(program, step);

					/* %eax <- [%eax] */
					load(program, next, eax, eax);
				}
				// D
				else {
					/* save our eax register */
					program.push(eax);

					/* get our BSS pointer */
					callFixHelper(program, step);

					/* %dst <- [%eax] */
					load(program, next, dst, eax);

					/* restore our eax register */
					program.pop(eax);
				}
			}
		}
		// 2026.01.04 - (A) test 42, (B) test 41 success&fail verified, (C) represented, (D) represented
	}

	private class Load32_8_Z extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOVZX r32, r/m8".equals(istr) && isGood(next);
		}

		protected void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src) {
			program.movzx(dst, AsmRegisters.byte_ptr(src, 0));
		}
		// 2016.01.03 represented in unit tests.
	}

	private class Load32_16_Z extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOVZX r32, r/m16".equals(istr) && isGood(next);
		}

		protected void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src) {
			program.movzx(dst, AsmRegisters.word_ptr(src, 0));
		}
		// 2016.01.03 represented in unit tests.
	}

	private class Load32_8_S extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOVSX r32, r/m8".equals(istr) && isGood(next);
		}

		protected void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src) {
			program.movsx(dst, AsmRegisters.byte_ptr(src, 0));
		}
		// 20126.01.03 represented in unit tests
	}

	private class Load32_16_S extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOVSX r32, r/m16".equals(istr) && isGood(next);
		}

		protected void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src) {
			program.movsx(dst, AsmRegisters.word_ptr(src, 0));
		}
		// 2026.01.03 represented in unit tests
	}

	private class Load32_32 extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, r/m32".equals(istr) && isGood(next);
		}

		protected void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src) {
			program.mov(dst, AsmRegisters.mem_ptr(src, 0));
		}
		// 2026.01.03 represented in unit tests.
	}

	private class LoadEAX implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV EAX, moffs32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

			/* get our BSS pointer */
			callFixHelper(program, step);

			/* store %eax in our original destination */
			program.mov(eax, AsmRegisters.mem_ptr(eax, 0));
		}
		// 2026.01.03 represented in unit tests.
	}

	private class LoadAddress implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r/m32, imm32".equals(istr) && next.getMemoryBase() != Register.NONE && next.getMemoryIndex() == Register.NONE;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32    eax = new AsmRegister32(ICRegisters.eax);
			AsmRegister32    ecx = new AsmRegister32(ICRegisters.ecx);
			AsmMemoryOperand dst = getMemOperand(next);

			/* validating that the relocation is for the data we want to store and not part of the address */
			if (!step.isRelocImmediate())
				noMatch(program, step, next);

			if (next.getMemoryBase() == Register.EAX) {
				program.push(ecx);
					program.push(eax);
						callFixHelper(program, step);
						program.mov(ecx, eax);
					program.pop(eax);
					program.mov(dst, ecx);
				program.pop(ecx);
			}
			else {
				/* save %eax */
				program.push(eax);

				/* do our GetBSS logic */
				callFixHelper(program, step);

				/* account for our push which changes the stack up somewhat */
				if (next.getMemoryBase() == Register.ESP)
					dst = dst.add(4);

				/* store %eax in our original destination */
				program.mov(dst, eax);

				/* restore %eax */
				program.pop(eax);
			}
		}
		// 2026.01.06 - EBP, ESP, EAX well represented in unit tests. EBX in test 40.
	}

	private class LoadAddressLEA implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "LEA r32, m".equals(istr) && next.getMemoryBase() != Register.NONE;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax  = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 esp  = new AsmRegister32(ICRegisters.esp);

			AsmRegister32 dst  = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			AsmRegister32 base = new AsmRegister32( new ICRegister(next.getMemoryBase()) );

			program.sub(esp, 4);
			program.mov(getStackPtr(), base);

			// A
			if (dst.equals(eax)) {
				CrystalUtils.print_warn(this.getClass().getSimpleName() + " LEA %eax, m found. Transform exists, but is untested.");
				CodeUtils.p(step);

				callFixHelper(program, step);
				program.add(eax, getStackPtr(0));
			}
			// B
			else {
				program.push(eax);
				callFixHelper(program, step);
				program.add(eax, getStackPtr(4));
				program.mov(dst, eax);
				program.pop(eax);
			}

			program.add(esp, 4);
		}
		// 2026.01.04 - (A) I couldn't create a working test, but I think the logic is simple enough. I'm going to keep this code
		//                  in place with a warning. If I encounter it later, will make a test. If it causes a crash (I hope not)--well,
		//                  at least the user had some warning.
		// 2026.01.04 - (B) test 44 (eax-base register only)
	}

	private class LoadAddressReg implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, imm32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 dst = new AsmRegister32( new ICRegister(next.getOp0Register()) );

			if (dst.equals(eax)) {
				callFixHelper(program, step);
			}
			else {
				/* save %eax */
				program.push(eax);

				/* do our GetBSS logic */
				callFixHelper(program, step);

				/* store %eax in our original destination */
				program.mov(dst, eax);

				/* restore %eax */
				program.pop(eax);
			}
		}
		// 2026.01.03 - represented in unit tests.
	}

	/* pulled from our x64 logic... with slight modifications (namely point to eax / AsmRegister32 types. */
	private abstract class StoreValue32 implements ModifyVerb {
		public abstract boolean is        (RegValue rax, RegValue dst);
		public abstract void    copyTemp  (CodeAssembler program,   RegValue tmp, RegValue src);
		public abstract void    storeValue(CodeAssembler program, RegValue dst, RegValue src);

		public AsmRegister32 getTmpReg(AsmRegister32 _rax) {
			return getRandReg32(_rax);
		}

		//Op0: MEM_OFFS
		//Op1: AL
		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 _rax = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 _tmp = getTmpReg(_rax);

			RegValue rax = RegValue.toRegValue(_rax);
			RegValue tmp = RegValue.toRegValue(_tmp);
			RegValue src = RegValue.toRegValue(next, 1);

			/* save rax now */
			program.push(_rax);

			if (is(rax, src)) {
				/* push our %tmp register, thanks */
				program.push(_tmp);

				/* put %rax int our %tmp register */
				copyTemp(program, tmp, rax);

				/* let's grab our BSS ptr */
				callFixHelper(program, step);

				/* store [rax] <- %tmp */
				storeValue(program, rax, tmp);

				/* restore our original tmp reg */
				program.pop(_tmp);
			}
			else {
				/* let's get our BSS ptr */
				callFixHelper(program, step);

				/* store src value at [%rax] */
				storeValue(program, rax, src);
			}

			/* restore original rax value */
			program.pop(_rax);
		}
	}

	private class Store32_32 extends StoreValue32 {
		public boolean check(String istr, Instruction next) {
			if ("MOV moffs32, EAX".equals(istr))
				return true;

			return "MOV r/m32, r32".equals(istr) && next.getMemoryBase() == Register.NONE;
		}

		public boolean is(RegValue rax, RegValue src) {
			return rax.getReg32().equals(src.getReg32());
		}

		public void copyTemp(CodeAssembler program, RegValue tmp, RegValue src) {
			program.mov(tmp.getReg32(), src.getReg32());
		}

		public void storeValue(CodeAssembler program, RegValue dst, RegValue src) {
			program.mov(AsmRegisters.mem_ptr(dst.getReg32(), 0), src.getReg32());
		}
		// 2026.01.03 - represented in unit tests.
	}

	private class Store32_16 extends StoreValue32 {
		public boolean check(String istr, Instruction next) {
			if ("MOV moffs16, AX".equals(istr))
				return true;

			return "MOV r/m16, r16".equals(istr) && next.getMemoryBase() == Register.NONE;
		}

		public boolean is(RegValue rax, RegValue src) {
			return rax.getReg16().equals(src.getReg16());
		}

		public void copyTemp(CodeAssembler program, RegValue tmp, RegValue src) {
			program.mov(tmp.getReg16(), src.getReg16());
		}

		public void storeValue(CodeAssembler program, RegValue dst, RegValue src) {
			program.mov(AsmRegisters.word_ptr(dst.getReg32(), 0), src.getReg16());
		}
		// 2026.01.03 - represented in unit tests.
	}

	private class Store32_8 extends StoreValue32 {
		public boolean check(String istr, Instruction next) {
			if ("MOV moffs8, AL".equals(istr))
				return true;

			return "MOV r/m8, r8".equals(istr) && next.getMemoryBase() == Register.NONE;
		}

		/* We need to limit our set of tmp registers to ebx/ecx/edx because esi/edi do not have
		 * a lower 8-bit register (e.g., sl, dl) in x86 arch */
		public AsmRegister32 getTmpReg(AsmRegister32 _rax) {
			switch (nextInt(3)) {
				case 0:
					return new AsmRegister32(ICRegisters.ebx);
				case 1:
					return new AsmRegister32(ICRegisters.ecx);
				case 2:
					return new AsmRegister32(ICRegisters.edx);
			}

			throw new RuntimeException("Bad tmp reg");
		}

		public boolean is(RegValue rax, RegValue src) {
			return rax.getReg8().equals(src.getReg8());
		}

		public void copyTemp(CodeAssembler program, RegValue tmp, RegValue src) {
			program.mov(tmp.getReg8(), src.getReg8());
		}

		public void storeValue(CodeAssembler program, RegValue dst, RegValue src) {
			program.mov(AsmRegisters.byte_ptr(dst.getReg32(), 0), src.getReg8());
		}
		// 2026.01.03 - represented in unit tests.
	}
}
