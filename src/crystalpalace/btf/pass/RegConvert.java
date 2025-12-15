package crystalpalace.btf.pass;

import crystalpalace.btf.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;
import com.github.icedland.iced.x86.info.*;

public class RegConvert {
	public static AsmRegister8 toReg8(AsmRegister32 reg) {
		if (reg.get() == ICRegisters.eax)
			return new AsmRegister8(ICRegisters.al);
		else if (reg.get() == ICRegisters.ebx)
			return new AsmRegister8(ICRegisters.bl);
		else if (reg.get() == ICRegisters.ecx)
			return new AsmRegister8(ICRegisters.cl);
		else if (reg.get() == ICRegisters.edi)
			return new AsmRegister8(ICRegisters.dil);
		else if (reg.get() == ICRegisters.edx)
			return new AsmRegister8(ICRegisters.dl);
		else if (reg.get() == ICRegisters.esi)
			return new AsmRegister8(ICRegisters.sil);

		return null;
	}

	public static AsmRegister16 toReg16(AsmRegister32 reg) {
		if (reg.get() == ICRegisters.eax)
			return new AsmRegister16(ICRegisters.ax);
		else if (reg.get() == ICRegisters.ebx)
			return new AsmRegister16(ICRegisters.bx);
		else if (reg.get() == ICRegisters.ecx)
			return new AsmRegister16(ICRegisters.cx);
		else if (reg.get() == ICRegisters.edi)
			return new AsmRegister16(ICRegisters.di);
		else if (reg.get() == ICRegisters.edx)
			return new AsmRegister16(ICRegisters.dx);
		else if (reg.get() == ICRegisters.esi)
			return new AsmRegister16(ICRegisters.si);

		return null;
	}

	public static AsmRegister64 toReg64(AsmRegister32 reg) {
		if (reg.get() == ICRegisters.eax)
			return new AsmRegister64(ICRegisters.rax);
		else if (reg.get() == ICRegisters.ebx)
			return new AsmRegister64(ICRegisters.rbx);
		else if (reg.get() == ICRegisters.ecx)
			return new AsmRegister64(ICRegisters.rcx);
		else if (reg.get() == ICRegisters.edi)
			return new AsmRegister64(ICRegisters.rdi);
		else if (reg.get() == ICRegisters.edx)
			return new AsmRegister64(ICRegisters.rdx);
		else if (reg.get() == ICRegisters.esi)
			return new AsmRegister64(ICRegisters.rsi);

		return null;
	}

	public static AsmRegister8 toReg8(AsmRegister64 reg) {
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

	public static AsmRegister16 toReg16(AsmRegister64 reg) {
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

	public static AsmRegister32 toReg32(AsmRegister64 reg) {
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
}
