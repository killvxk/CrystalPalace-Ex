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

public class FixBSSReferencesX64 extends BaseModify {
	protected String getbss  = null;
	protected int    bsssize = 0;

	public void setupVerbs() {
		verbs.add(new LoadAddress());

		verbs.add(new Load32_8());
		verbs.add(new Load32_16());
		verbs.add(new Load32_32());

		verbs.add(new Load64_64());

		verbs.add(new Store8());
		verbs.add(new Store16());
		verbs.add(new Store32());
		verbs.add(new Store64());

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

	public FixBSSReferencesX64(Code code, String getbss) {
		super(code);
		this.getbss = getbss;
	}

	public void callGetBSS(CodeAssembler program, RebuildStep step) {
		int bsslen = object.getSection(".bss").getRawData().length;

		AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);
		AsmRegister32 ecx = new AsmRegister32(ICRegisters.ecx);

		/* save registers */
		List saved = pushad(program);

		/* create our shadowspace for x64 ABI */
		stackAlloc(program, 0x20);

		/* call our getBSS function */
		program.mov(ecx, bsslen);
		program.call(step.getLabel(getbss));

		/* get rid of our x64 ABI shadowspace */
		stackDealloc(program, 0x20);

		/* restore registers */
		popad(program, saved);

		/* NOW, let's adjust %rax based on the offset into .bss */
		int bssoffset = step.getInstructionLength() - (step.getRelocOffset() + step.getRelocation().getFromOffset());
		    bssoffset = bssoffset + step.getRelocation().getOffsetAsLong();

		if (bssoffset != 0)
			program.add(rax, bssoffset);
	}

	private class StoreConstant implements ModifyVerb {
		protected Set valid = new HashSet();

		public StoreConstant() {
			valid.add("MOV r/m8, imm8");
			valid.add("MOV r/m16, imm16");
			valid.add("MOV r/m32, imm32");
			valid.add("MOV r/m64, imm32");
		}

		public boolean check(String istr, Instruction next) {
			return valid.contains(istr) && next.isIPRelativeMemoryOperand();
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			int           val  = next.getImmediate32();
			String        istr = next.getOpCode().toInstructionString();
			AsmRegister64 rax  = new AsmRegister64(ICRegisters.rax);

			/* push our %rax register */
			program.push(rax);

			/* let's get our .bss pointer */
			callGetBSS(program, step);

			/* [rax] <- val ; type hint (e.g., byte_ptr) dictates the instruction that gets generated */
			if ("MOV r/m8, imm8".equals(istr)) {
				program.mov(AsmRegisters.byte_ptr(rax, 0), val);
			}
			else if ("MOV r/m16, imm16".equals(istr)) {
				program.mov(AsmRegisters.word_ptr(rax, 0), val);
			}
			else if ("MOV r/m32, imm32".equals(istr)) {
				program.mov(AsmRegisters.dword_ptr(rax, 0), val);
			}
			else if ("MOV r/m64, imm32".equals(istr)) {
				program.mov(AsmRegisters.qword_ptr(rax, 0), val);
			}
			else {
				throw new RuntimeException("Invalid istr " + istr + " in StoreConstant");
			}

			/* bring %rax back */
			program.pop(rax);

			/* resolve it! */
			step.resolve();
		}
	}

	private abstract class StoreValue implements ModifyVerb {
		public abstract boolean is        (RegValue rax, RegValue dst);
		public abstract void    copyTemp  (CodeAssembler program,   RegValue tmp, RegValue src);
		public abstract void    storeValue(CodeAssembler program, RegValue dst, RegValue src);

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister64 _rax = new AsmRegister64(ICRegisters.rax);
			AsmRegister64 _tmp = getRandReg64(_rax);

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

	private class Store8 extends StoreValue {
		public boolean check(String istr, Instruction next) {
			return "MOV r/m8, r8".equals(istr) && next.isIPRelativeMemoryOperand();
		}

		public boolean is(RegValue rax, RegValue src) {
			return rax.getReg8().equals(src.getReg8());
		}

		public void copyTemp(CodeAssembler program, RegValue tmp, RegValue src) {
			program.mov(tmp.getReg8(), src.getReg8());
		}

		public void storeValue(CodeAssembler program, RegValue dst, RegValue src) {
			program.mov(AsmRegisters.mem_ptr(dst.getReg64(), 0), src.getReg8());
		}
	}

	private class Store16 extends StoreValue {
		public boolean check(String istr, Instruction next) {
			return "MOV r/m16, r16".equals(istr) && next.isIPRelativeMemoryOperand();
		}

		public boolean is(RegValue rax, RegValue src) {
			return rax.getReg16().equals(src.getReg16());
		}

		public void copyTemp(CodeAssembler program, RegValue tmp, RegValue src) {
			program.mov(tmp.getReg16(), src.getReg16());
		}

		public void storeValue(CodeAssembler program, RegValue dst, RegValue src) {
			program.mov(AsmRegisters.mem_ptr(dst.getReg64(), 0), src.getReg16());
		}
	}

	private class Store32 extends StoreValue {
		public boolean check(String istr, Instruction next) {
			return "MOV r/m32, r32".equals(istr) && next.isIPRelativeMemoryOperand();
		}

		public boolean is(RegValue rax, RegValue src) {
			return rax.getReg32().equals(src.getReg32());
		}

		public void copyTemp(CodeAssembler program, RegValue tmp, RegValue src) {
			program.mov(tmp.getReg32(), src.getReg32());
		}

		public void storeValue(CodeAssembler program, RegValue dst, RegValue src) {
			program.mov(AsmRegisters.mem_ptr(dst.getReg64(), 0), src.getReg32());
		}
	}

	private class Store64 extends StoreValue {
		public boolean check(String istr, Instruction next) {
			return "MOV r/m64, r64".equals(istr) && next.isIPRelativeMemoryOperand();
		}

		public boolean is(RegValue rax, RegValue src) {
			return rax.getReg64().equals(src.getReg64());
		}

		public void copyTemp(CodeAssembler program, RegValue tmp, RegValue src) {
			program.mov(tmp.getReg64(), src.getReg64());
		}

		public void storeValue(CodeAssembler program, RegValue dst, RegValue src) {
			program.mov(AsmRegisters.mem_ptr(dst.getReg64(), 0), src.getReg64());
		}
	}

	private abstract class LoadValue32 implements ModifyVerb {
		public abstract void loadValue(CodeAssembler program, AsmRegister32 dst, AsmRegister64 src);

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 dst = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
			AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);

			if (dst.equals(eax)) {
				/* let's grab our BSS ptr */
				callGetBSS(program, step);

				/* handle everything else now, please */
				loadValue(program, eax, rax);
			}
			else {
				/* save RAX too */
				program.push(rax);

				/* let's grab our BSS ptr */
				callGetBSS(program, step);

				/* handle everything else now, please */
				loadValue(program, dst, rax);

				/* restore rax */
				program.pop(rax);
			}

			/* mark this instruction-associated relocation as resolved */
			step.resolve();
		}
	}

	private class Load32_8 extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOVZX r32, r/m8".equals(istr) && next.isIPRelativeMemoryOperand();
		}

		public void loadValue(CodeAssembler program, AsmRegister32 dst, AsmRegister64 src) {
			program.movzx(dst, AsmRegisters.byte_ptr(src, 0));
		}
	}

	private class Load32_16 extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOVZX r32, r/m16".equals(istr) && next.isIPRelativeMemoryOperand();
		}

		public void loadValue(CodeAssembler program, AsmRegister32 dst, AsmRegister64 src) {
			program.movzx(dst, AsmRegisters.word_ptr(src, 0));
		}
	}

	private class Load32_32 extends LoadValue32 {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, r/m32".equals(istr) && next.isIPRelativeMemoryOperand();
		}

		public void loadValue(CodeAssembler program, AsmRegister32 dst, AsmRegister64 src) {
			program.mov(dst, AsmRegisters.mem_ptr(src, 0));
		}
	}

	private class Load64_64 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r64, r/m64".equals(istr) && next.isIPRelativeMemoryOperand();
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister64 dst = new AsmRegister64( new ICRegister(next.getOp0Register()) );
			AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);

			if (dst.equals(rax)) {
				/* let's grab our BSS ptr */
				callGetBSS(program, step);

				/* handle everything else now, please */
				program.mov(rax, AsmRegisters.mem_ptr(rax, 0));
			}
			else {
				/* save RAX too */
				program.push(rax);

				/* let's grab our BSS ptr */
				callGetBSS(program, step);

				/* handle everything else now, please */
				program.mov(dst, AsmRegisters.mem_ptr(rax, 0));

				/* restore rax */
				program.pop(rax);
			}

			/* mark this instruction-associated relocation as resolved */
			step.resolve();
		}
	}

	private class LoadAddress implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "LEA r64, m".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);
			AsmRegister64 dst = new AsmRegister64( new ICRegister(next.getOp0Register()) );

			if (dst.equals(rax)) {
				/* get our .bss pointer */
				callGetBSS(program, step);
			}
			else {
				/* save RAX too */
				program.push(rax);

				/* get our .bss pointer */
				callGetBSS(program, step);

				/* handle everything else now, please */
				program.mov(dst, rax);

				/* restore rax */
				program.pop(rax);
			}

			/* mark this instruction-associated relocation as resolved */
			step.resolve();
		}
	}
}
