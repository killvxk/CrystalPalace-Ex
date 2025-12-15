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

public class FixBSSReferencesX86 extends BaseModify {
	protected String getbss  = null;
	protected int    bsssize = 0;

	public void setupVerbs() {
		/* our load address ops */
		verbs.add(new LoadAddress());
		verbs.add(new LoadAddressReg());

		/* load value ops */
		verbs.add(new LoadEAX());
		verbs.add(new Load32_8());
		verbs.add(new Load32_16());
		verbs.add(new Load32_32());

		/* store value ops */
		verbs.add(new Store32_32());
		verbs.add(new Store32_16());
		verbs.add(new Store32_8());

		/* store constant ops */
		verbs.add(new StoreConstant());
	}

	public boolean shouldModify(RebuildStep step, Instruction next) {
		/* we're only interested, in instructions associated with relocations */
		if (!step.hasRelocation())
			return false;

		/* we're working with .bss references */
		if ( ".bss".equals(step.getRelocation().getSymbolName()) ) {
			return true;
		}

		return false;
	}

	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		System.out.println(next.getOpCode().toInstructionString());
		CodeUtils.printInst(code, next);
		CodeInfo.Dump(next, null);

		throw new RuntimeException(this.getClass().getSimpleName() + ": Can't transform '" + step.getInstructionString() + "' to handle " + step.getRelocation() + ". Change your compiler optimization settings or turn off optimizations for this function.");
	}

	public FixBSSReferencesX86(Code code, String getbss) {
		super(code);
		this.getbss = getbss;
	}

	public void callGetBSS(CodeAssembler program, RebuildStep step) {
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
		program.add(eax, step.getRelocation().getOffsetAsLong());
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

			/* push our %eax register */
			program.push(eax);

			/* let's get our .bss pointer */
			callGetBSS(program, step);

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

			/* resolve it! */
			step.resolve();
		}
	}

	private abstract class LoadValue32 implements ModifyVerb {
		protected abstract void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src);

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 dst = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

			if (dst.equals(eax)) {
				/* get our BSS pointer */
				callGetBSS(program, step);

				/* %eax <- [%eax] */
				load(program, next, eax, eax);
			}
			else {
				/* save our eax register */
				program.push(eax);

				/* get our BSS pointer */
				callGetBSS(program, step);

				/* %dst <- [%eax] */
				load(program, next, dst, eax);

				/* restore our eax register */
				program.pop(eax);
			}

			step.resolve();
		}
	}

	private class Load32_8 extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOVZX r32, r/m8".equals(istr) && next.getMemoryBase() == Register.NONE;
		}

		protected void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src) {
			program.movzx(dst, AsmRegisters.byte_ptr(src, 0));
		}
	}

	private class Load32_16 extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOVZX r32, r/m16".equals(istr) && next.getMemoryBase() == Register.NONE;
		}

		protected void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src) {
			program.movzx(dst, AsmRegisters.word_ptr(src, 0));
		}
	}

	private class Load32_32 extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, r/m32".equals(istr) && next.getMemoryBase() == Register.NONE;
		}

		protected void load(CodeAssembler program, Instruction next, AsmRegister32 dst, AsmRegister32 src) {
			program.mov(dst, AsmRegisters.mem_ptr(src, 0));
		}
	}

	private class LoadEAX implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV EAX, moffs32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

			/* get our BSS pointer */
			callGetBSS(program, step);

			/* store %eax in our original destination */
			program.mov(eax, AsmRegisters.mem_ptr(eax, 0));

			/* mark this instruction-associated relocation as resolved */
			step.resolve();
		}
	}

	private class LoadAddress implements ModifyVerb {
		public boolean isDesiredReg(Instruction next) {
			return next.getMemoryBase() == Register.EBP || next.getMemoryBase() == Register.ESP;
		}

		public boolean check(String istr, Instruction next) {
			return "MOV r/m32, imm32".equals(istr) && isDesiredReg(next);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32    eax = new AsmRegister32(ICRegisters.eax);
			AsmMemoryOperand dst = getMemOperand(next);

			/* save %eax */
			program.push(eax);

			/* do our GetBSS logic */
			callGetBSS(program, step);

			/* account for our push which changes the stack up somewhat */
			if (next.getMemoryBase() == Register.ESP)
				dst = dst.add(4);

			/* store %eax in our original destination */
			program.mov(dst, eax);

			/* restore %eax */
			program.pop(eax);

			/* mark this instruction-associated relocation as resolved */
			step.resolve();
		}
	}

	private class LoadAddressReg implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, imm32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 dst = new AsmRegister32( new ICRegister(next.getOp0Register()) );

			if (dst.equals(eax)) {
				callGetBSS(program, step);
			}
			else {
				/* save %eax */
				program.push(eax);

				/* do our GetBSS logic */
				callGetBSS(program, step);

				/* store %eax in our original destination */
				program.mov(dst, eax);

				/* restore %eax */
				program.pop(eax);
			}

			/* mark this instruction-associated relocation as resolved */
			step.resolve();
		}
	}

	/* pulled from our x64 logic... with slight modifications (namely point to eax / AsmRegister32 types. */
	private abstract class StoreValue32 implements ModifyVerb {
		public abstract boolean is        (RegValue rax, RegValue dst);
		public abstract void    copyTemp  (CodeAssembler program,   RegValue tmp, RegValue src);
		public abstract void    storeValue(CodeAssembler program, RegValue dst, RegValue src);

		public AsmRegister32 getTmpReg(AsmRegister32 _rax) {
			return getRandReg32(_rax);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 _rax = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 _tmp = getTmpReg(_rax);

			RegValue rax = RegValue.toRegValue(_rax);
			RegValue tmp = RegValue.toRegValue(_tmp);
			RegValue src = RegValue.toRegValue(next, 1);

			if (is(rax, src)) {
				/* push our %tmp register, thanks */
				program.push(_tmp);

				/* put %rax int our %tmp register */
				copyTemp(program, tmp, src);

				/* let's grab our BSS ptr */
				callGetBSS(program, step);

				/* store [rax] <- %tmp */
				storeValue(program, rax, tmp);

				/* restore our original tmp reg */
				program.pop(_tmp);
			}
			else {
				/* push our %rax register, please */
				program.push(_rax);

				/* let's get our BSS ptr */
				callGetBSS(program, step);

				/* store src value at [%rax] */
				storeValue(program, rax, src);

				/* restore original rax value */
				program.pop(_rax);
			}

			/* mark this instruction-associated relocation as resolved */
			step.resolve();
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
	}
}
