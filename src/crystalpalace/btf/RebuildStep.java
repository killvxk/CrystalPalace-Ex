package crystalpalace.btf;

import crystalpalace.btf.lttl.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import crystalpalace.coff.*;
import java.util.*;

/* This is sort-of a catch-all class with utilities to access and do things within
 * our Rebuilder and to work with data tied to the current Instruction */
public class RebuildStep {
	protected Rebuilder    builder;
	protected Instruction  instruction;
	protected String       function;
	protected ListIterator iterator;

	public RebuildStep(Rebuilder builder) {
		this.builder = builder;
	}

	public void enter(String function, ListIterator iterator) {
		this.function = function;
		this.iterator = iterator;
	}

	/* get the next instruction, but protect the integrity of our cursor into the list */
	public Instruction peekNext() {
		Instruction next = null;

		if (iterator.hasNext()) {
			next = (Instruction)iterator.next();
			iterator.previous();
		}

		return next;
	}

	/* consume the next instruction */
	public void consumeNext() {
		iterator.next();
	}

	/*
	 * Determine if instruction is in a zone between RFLAGS/EFLAGS being modified and acted on/used.
	 */
	public boolean isDangerous() {
		return builder.getZones().isDangerous(instruction);
	}

	/*
	 * Determine whether or not the current function is a "dirty" leaf function (e.g., rsp is NOT aligned
	 * due to a compiler optimization)
	 */
	public boolean isDirty() {
		return builder.getLeaves().isDirty(getFunction());
	}

	public void step(Instruction inst) {
		instruction = inst;
	}

	public int getInstructionLength() {
		return instruction.getLength();
	}

	public String getInstructionString() {
		return String.format("%016X %s", instruction.getIP(), instruction.getOpCode().toInstructionString());
	}

	public boolean isRelocDisplacement() {
		return getRelocOffset() == builder.analysis.getOffsets(instruction).displacementOffset;
	}

	public boolean isRelocImmediate() {
		return getRelocOffset() == builder.analysis.getOffsets(instruction).immediateOffset;
	}

	public int getRelocOffset() {
		RelocationFix fix = builder.getRelocations().get(getRelocation());
		return fix.instOffset;
	}

	public void setRelocOffset(int x) {
		RelocationFix fix = builder.getRelocations().get(getRelocation());
		fix.instOffset = x;
	}

	public void setRelocOffsetValue(long x) {
		RelocationFix fix = builder.getRelocations().get(getRelocation());
		fix.setRelocOffsetValue(x);
	}

	public long getRelocOffsetValue() {
		RelocationFix fix = builder.getRelocations().get(getRelocation());
		return fix.getRelocOffsetLong();
	}

	/* this is x86 only */
	public void createRelocationFor(CodeAssembler program, Symbol value, int instOffset) {
		CodeLabel               label = program.createLabel();
		program.label(label);
		Relocation              reloc = new Relocation(builder.object.getSection(".text"), 0, ".text", Relocation.IMAGE_REL_I386_DIR32);
		RelocationFix           fix   = builder.getRelocations().add(reloc, label, instOffset);
		fix.setRelocOffsetValue(value.getValue());
	}

	public void createLabel(CodeAssembler program) {
		CodeLabel label = program.createLabel();
		program.label( label );

		RelocationFix fix = builder.getRelocations().get(getRelocation());
		fix.label = label;
	}

	public Relocation getRelocation() {
		return builder.analysis.getRelocation(instruction);
	}

	public boolean hasRelocation() {
		return builder.analysis.hasRelocation(instruction);
	}

	/* resolve the curret relocation (that is... remove it and mark it as done!) */
	public void resolve() {
		builder.getRelocations().resolve(getRelocation());
	}

	public String getFunction() {
		return function;
	}

	public CodeLabel getLabel(String func) {
		return builder.labels.getLabel(func);
	}
}
