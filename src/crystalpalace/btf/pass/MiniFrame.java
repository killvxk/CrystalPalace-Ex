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

/* Setup a "mini" x64 stack frame for our instrumentation calls */
public class MiniFrame {
	protected int  size    = 0;
	protected List slots   = new LinkedList();

	private static interface Slot {
		public void setup(CodeAssembler program);
		public void cleanup(CodeAssembler program);
	}

	private static class RegSlot implements Slot {
		protected AsmRegister64    reg;
		protected AsmMemoryOperand memptr;

		public RegSlot(AsmRegister64 reg, AsmMemoryOperand memptr) {
			this.reg    = reg;
			this.memptr = memptr;
		}

		public void setup(CodeAssembler program) {
			program.mov(memptr, reg);
		}

		public void cleanup(CodeAssembler program) {
			program.mov(reg, memptr);
		}
	}

	private static class ConstantSlot implements Slot {
		protected long             value;
		protected AsmMemoryOperand memptr;

		public ConstantSlot(long value, AsmMemoryOperand memptr) {
			this.value  = value;
			this.memptr = memptr;
		}

		public void setup(CodeAssembler program) {
			AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);

			if (value == 0) {
				program.xor(rax, rax);
				program.mov(memptr, rax);
			}
			else {
				program.mov(rax, value);
				program.mov(memptr, rax);
			}
		}

		public void cleanup(CodeAssembler program) {
		}
	}

	public MiniFrame() {
		size += 0x20; /* we want shadowspace at the bottom of our mini-frame */
	}

	public AsmMemoryOperand getStackPtr() {
		return AsmRegisters.mem_ptr(new AsmRegister64(ICRegisters.rsp), size);
	}

	public AsmMemoryOperand getAndIncStackPtr() {
		AsmMemoryOperand value = getStackPtr();
		size += 8;
		return value;
	}

	public void saveRegs(RebuildStep step) {
		List regs = new LinkedList(); // TODO: implement as: step.getUsedRegisters().getRegs();
		regs.add(new AsmRegister64(ICRegisters.rcx));
		regs.add(new AsmRegister64(ICRegisters.rdx));
		regs.add(new AsmRegister64(ICRegisters.r8));
		regs.add(new AsmRegister64(ICRegisters.r9));
		regs.add(new AsmRegister64(ICRegisters.r10));
		regs.add(new AsmRegister64(ICRegisters.r11));

		Collections.shuffle(regs);

		Iterator i = regs.iterator();
		while (i.hasNext()) {
			slots.add(new RegSlot(  (AsmRegister64)i.next(), getAndIncStackPtr()  ));
		}

		/* only needed if we start optimizing which regs we save */
//		if ((size % 16) != 0) {
//			size += 8;
//		}
	}

	public AsmMemoryOperand pushString(String text) {
		/* generate a zero-terminated byte array with our string */
		Concat temp = new Concat();
		temp.add(CrystalUtils.toBytes(text, "UTF-8"));
		temp.add(new byte[1]); // null terminator

		/* align it to the right size for our arch */
		temp.align(16);

		/* this is where on our stack our string lives? */
		AsmMemoryOperand location = getStackPtr();

		/* create a ByteWalker */
		ByteWalker walker = new ByteWalker(temp.get());
		walker.little();

		/* walk and create each entry to dump into our stack */
		while (!walker.isComplete()) {
			long val = walker.readLong();
			slots.add(new ConstantSlot( val, getAndIncStackPtr() ));
		}

		return location;
	}

	public void start(CodeAssembler program) {
		/* sanity check */
		if ((size % 16) != 0)
			throw new RuntimeException("MiniFrame x64 alignment is: " + (size % 16) + " which is not MOD 16");

		/* adjust the stack pointer for our mini-frame */
		AsmRegister64 rsp = new AsmRegister64(ICRegisters.rsp);
		program.sub(rsp, size);

		/* walk through our data in various slots and plunk them down. */
		Collections.shuffle(slots);

		Iterator i = slots.iterator();
		while (i.hasNext()) {
			Slot slot = (Slot)i.next();
			slot.setup(program);
		}
	}

	public void done(CodeAssembler program) {
		Collections.shuffle(slots);

		/* restore our registers, using moves please */
		Iterator i = slots.iterator();
		while (i.hasNext()) {
			Slot slot = (Slot)i.next();
			slot.cleanup(program);
		}

		/* tear down the mini-frame */
		AsmRegister64 rsp = new AsmRegister64(ICRegisters.rsp);
		program.add(rsp, size);
	}
}
