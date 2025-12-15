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

public class FixX86References extends BaseModify {
	protected String      retaddr   = null;

	public void setupVerbs() {
		verbs.add(new MovR32Imm32());
		verbs.add(new MovMem32Imm32());
		verbs.add(new MovRelocEAX());
		verbs.add(new MovRelocNotEAX());
		verbs.add(new CmpEax());
	}

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
		if ( ".bss".equals(step.getRelocation().getSymbolName()) )
			return false;

		/* technically, I don't think this form should happen here. We either know what's in our
		 * local module (rel32) or not. This does come up though with bad function names. */
		String istr = next.getOpCode().toInstructionString();
		if ("CALL rel32".equals(istr))
			return false;

		return true;
	}

	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		System.out.println(next.getOpCode().toInstructionString());
		CodeUtils.printInst(code, next);
		CodeInfo.Dump(next, null);

		throw new RuntimeException(this.getClass().getSimpleName() + ": Can't transform '" + step.getInstructionString() + "' to handle " + step.getRelocation() + ". Change your compiler optimization settings or turn off optimizations for this function.");
	}

	public FixX86References(Code code, String retaddr) {
		super(code);
		this.retaddr  = retaddr;
	}

	private class CmpEax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CMP EAX, imm32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

			// push ecx/edx
			List saved = pushad(program);
			AsmRegister32 tmp = getReg32(saved, null);

			// push %eax
			// call _caller
			// mov $my_data, %tmp
			// add $5, %tmp
			// add %eax. %tmp
			// pop %eax
			program.push(eax);

			program.call(step.getLabel(retaddr));

			step.createLabel(program);
			step.setRelocOffset(1);
			program.mov(tmp, next.getImmediate32());

			program.add(tmp, 5);
			program.add(tmp, eax);

			program.pop(eax);

			// cmp %eax, %tmp
			program.cmp(eax, tmp);

			// pop ecx/edx
			popad(program, saved);
		}
		/* 10/26/2025 - represented in unit tests 19 and 21. */
	}

	private class MovMem32Imm32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r/m32, imm32".equals(istr) && (next.getMemoryBase() == Register.EBP || next.getMemoryBase() == Register.ESP || next.getMemoryBase() == Register.EAX);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmMemoryOperand dst = getMemOperand(next);
			AsmRegister32    eax = new AsmRegister32(ICRegisters.eax);

			// push ecx/edx
			List saved = pushad(program);
			AsmRegister32 tmp = getReg32(saved, null);

			program.push(eax);
			program.call(step.getLabel(retaddr));

			step.createLabel(program);
			step.setRelocOffset(1);
			program.mov(tmp, 0);

			program.add(tmp, 5 );
			program.add(tmp, eax);

			program.pop(eax);

			/* if we're displacing off of the stack, we need to add 4b to account for the space we created */
			if (next.getMemoryBase() == Register.ESP)
				dst = dst.add(8);
			program.mov(dst, tmp);

			// pop ecx/edx
			popad(program, saved);
		}
		/* 10/26/2025 - EBP, ESP, and EAX cases are represented in unit tests. */
	}

	private class MovRelocNotEAX implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, r/m32".equals(istr) && next.getOp0Register() != Register.EAX;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 dst = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

			// save eax
			program.push(eax);

			// push ecx/edx
			List saved = pushad(program);
			AsmRegister32 tmp = (AsmRegister32)getReg32(saved, null);

			// push %tmp
			// call _caller
			// mov moffs32, %tmp
			// add $5, %tmp
			// add %eax, %tmp
			// mov [%tmp], %eax
			// pop %tmp
			program.call(step.getLabel(retaddr));
			step.createLabel(program);
			step.setRelocOffset(1);
			program.mov(tmp, next.getImmediate32());
			program.add(tmp, 5 );  /* I'm presuming our program.mov(tmp, moffs32) is 5 bytes */
			program.add(tmp, eax );
			program.mov(eax, AsmRegisters.mem_ptr(tmp)); /* Crucial, because we need to do a LOAD now */

			// pop ecx/edx
			popad(program, saved);

			// mov eax into our dst register
			program.mov(dst, eax);

			// pop eax
			program.pop(eax);
		}
		/* 10/26/2025 - represented in unit test 27. */
	}

	private class MovRelocEAX implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV EAX, moffs32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

			// push ecx/edx
			List saved = pushad(program);
			AsmRegister32 tmp = (AsmRegister32)getReg32(saved, null);

			// push %tmp
			// call _caller
			// mov moffs32, %tmp
			// add $5, %tmp
			// add %eax, %tmp
			// mov [%tmp], %eax
			// pop %tmp
			program.call(step.getLabel(retaddr));

			step.createLabel(program);
			program.mov(tmp, next.getImmediate32());
			program.add(tmp, 5 );  /* I'm presuming our program.mov(tmp, moffs32) is 5 bytes */
			program.add(tmp, eax );
			program.mov(eax, AsmRegisters.mem_ptr(tmp)); /* Crucial, because we need to do a LOAD now */

			// pop ecx/edx
			popad(program, saved);
		}
		// 10/26/25 - represented in unit test 27
	}

	private class MovR32Imm32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, imm32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 dst = new AsmRegister32( new ICRegister(next.getOp0Register()) );
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

			// push %tmp
			// call _caller
			// mov $my_data, %tmp
			// add $5, %tmp
			// add %tmp, %eax
			// pop %tmp
			if (dst.equals(eax)) {
				// push ecx/edx
				List saved = pushad(program);
				AsmRegister32 tmp = getReg32(saved, dst);

				program.call(step.getLabel(retaddr));

				step.createLabel(program);
				next.setOp0Register( tmp.getRegister() );
				program.addInstruction(next);

				program.add(tmp, step.getInstructionLength() );
				program.add(eax, tmp);

				// pop ecx/edx
				popad(program, saved);
			}
			// push %eax
			// pushad (EXCL %dst)
			// call _caller
			// mov $my_data, %dst
			// add $5, %dst
			// add %dst, %eax
			// popad
			// pop %eax
			else {
				// save eax
				program.push(eax);

				// push ecx/edx
				List saved = pushad(program, dst);

				program.call(step.getLabel(retaddr));

				step.createLabel(program);
				program.addInstruction(next);

				program.add(dst, step.getInstructionLength() );
				program.add(dst, eax);

				// pop ecx/edx
				popad(program, saved);

				// restore eax
				program.pop(eax);
			}
		}
		// 10/26/2025 - non-EAX case represented in unit test 29, EAX case is common and across unit tests
	}
}
