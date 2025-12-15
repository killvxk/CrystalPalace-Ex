package crystalpalace.btf.pass;

import crystalpalace.btf.*;
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

/*
 * Some of our transformation strategies require a case-by-case interpretation of whether to pull a
 * byte, word, dword, or qword register out of an instruction and other times, the same logic might call
 * for doing the same from a stand-in temporary register. This class is meant to make it a lot easier to
 * avoid this type boilerplate when writing out the logic of a transformation.
 */
public abstract class RegValue {
	public abstract AsmRegister8  getReg8();
	public abstract AsmRegister16 getReg16();
	public abstract AsmRegister32 getReg32();
	public abstract AsmRegister64 getReg64();

	private static class RegValue64 extends RegValue {
		protected AsmRegister64 reg;

		public RegValue64(AsmRegister64 reg) {
			this.reg = reg;
		}

		public AsmRegister8 getReg8() {
			return RegConvert.toReg8(reg);
		}

		public AsmRegister16 getReg16() {
			return RegConvert.toReg16(reg);
		}

		public AsmRegister32 getReg32() {
			return RegConvert.toReg32(reg);
		}

		public AsmRegister64 getReg64() {
			return reg;
		}
	}

	private static class RegValue32 extends RegValue {
		protected AsmRegister32 reg;

		public RegValue32(AsmRegister32 reg) {
			this.reg = reg;
		}

		public AsmRegister8 getReg8() {
			return RegConvert.toReg8(reg);
		}

		public AsmRegister16 getReg16() {
			return RegConvert.toReg16(reg);
		}

		public AsmRegister32 getReg32() {
			return reg;
		}

		public AsmRegister64 getReg64() {
			throw new RuntimeException("I won't convert a 32b base reg to 64b - not implemented");
		}
	}

	public static RegValue toRegValue(AsmRegister64 reg) {
		return new RegValue64(reg);
	}

	public static RegValue toRegValue(AsmRegister32 reg) {
		return new RegValue32(reg);
	}

	private static class RegValueInst extends RegValue {
		protected Instruction next;
		protected int         opNo;

		public RegValueInst(Instruction next, int opNo) {
			this.next = next;
			this.opNo = opNo;
		}

		public ICRegister getOpReg() {
			if (opNo == 0) {
				return new ICRegister(next.getOp0Register());
			}
			else if (opNo == 1) {
				return new ICRegister(next.getOp1Register());
			}

			throw new RuntimeException("getOpreg() doesn't support " + opNo);
		}

		public AsmRegister8 getReg8() {
			return new AsmRegister8( getOpReg() );
		}

		public AsmRegister16 getReg16() {
			return new AsmRegister16( getOpReg() );
		}

		public AsmRegister32 getReg32() {
			return new AsmRegister32( getOpReg() );
		}

		public AsmRegister64 getReg64() {
			return new AsmRegister64( getOpReg() );
		}
	}

	public static RegValue toRegValue(Instruction next, int opNo) {
		return new RegValueInst(next, opNo);
	}
}
