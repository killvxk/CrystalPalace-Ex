package crystalpalace.btf.lttl;

import crystalpalace.btf.*;
import crystalpalace.btf.Code;
import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;
import com.github.icedland.iced.x86.fmt.fast.*;
import com.github.icedland.iced.x86.info.*;

/* keep track of our jump targets and info about them, *phew* */
public class Jumps implements CodeVisitor {
	protected CodeAssembler program;
	protected Code          analysis;

	protected Map           targets = new HashMap();
	protected Map           jumps   = new HashMap();

	public Jumps(Code analysis, CodeAssembler program) {
		this.analysis = analysis;
		this.program  = program;
	}

	/* determine if the next instruction is a jump target */
	public boolean hasLabel(Instruction next) {
		return targets.containsKey(next.getIP());
	}

	/* get the label for our jump target, we need to declare it, y'know. */
	public CodeLabel getLabel(Instruction next) {
		return (CodeLabel)targets.get(next.getIP());
	}

	/* is this a jump instruction we need to rewrite or work with */
	public boolean isJump(Instruction next) {
		return jumps.containsKey(next);
	}

	/* return the label for our jump, thanks! */
	public CodeLabel getJumpLabel(Instruction next) {
		return (CodeLabel)jumps.get(next);
	}

	/* we're creating and caching our labels here, so there's only one (ideally) per
	   location */
	public CodeLabel createLabel(long target) {
		CodeLabel label  = null;

		/*
		 * Create our label for our target, IF we need to
		 */
		if (targets.containsKey(target)) {
			label = (CodeLabel)targets.get(target);
		}
		else {
			label = program.createLabel();
			targets.put(target, label);
		}

		return label;
	}

	public void add(Instruction inst) {
		/* add our label as a target label, right? */
		CodeLabel label  = createLabel(inst.getMemoryDisplacement32());

		/*
		 * NOTE, Instruction used as a Map key will collide with other like-instructions
		 * regardless of RIP/EIP. This is OK here, because iced normalizes jump targets to
		 * the address they target and labels are re-used vs. generated on each request.
		 * But, beware of this behavior.
		 *
		 * 00000000000008A6 EB3E                 jmp       0x0000`0000`0000`08E6
		 * 0000000000000879 EB6B                 jmp       0x0000`0000`0000`08E6
		 */

		/* associate this label with our jump instruction itself */
		jumps.put(inst, label);
	}

	/* build up our knowledge of the code and the jump targets */
	public void visit(Instruction next) {
		/* je and friends */
		if (next.isJccShortOrNear()) {
			add(next);
			//CodeUtils.details(analysis, "Jcc", next);
		}
		else if (next.isCallNear()) {
			//add(next);
			//CodeUtils.details(analysis, "Call (Near)", next);
		}
		else if (next.isJcxShort()) {
			add(next);
			//CodeUtils.details(analysis, "Jcx", next);
		}
		/* https://stackoverflow.com/questions/63544880/what-are-jkzd-and-jknzd ??? */
		else if (next.isJkccShortOrNear()) {
			add(next);
			//CodeUtils.details(analysis, "Jkcc", next);
		}
		else if (next.isJmpFar()) {
			/* better hope there's a relocation here... I don't think we want to rewrite this target */
			CodeUtils.details(analysis, "Jmp FAR", next);
		}
		/* jmp */
		else if (next.isJmpShortOrNear()) {
			add(next);
			//CodeUtils.details(analysis, "Jmp", next);
		}
		/* loop */
		else if (next.isLoop()) {
			add(next);
			//CodeUtils.details(analysis, "loop", next);
		}
		/* loopcc */
		else if (next.isLoopcc()) {
			add(next);
			//CodeUtils.details(analysis, "loopcc", next);
		}
	}

	public void handleLabel(Instruction inst) {
		try {
			if (hasLabel(inst))
				program.label( getLabel(inst) );
		}
		catch (IllegalArgumentException ex) {
			throw new RuntimeException( "Can't label '" + String.format("%016X %s", inst.getIP(), inst.getOpCode().toInstructionString()) + "'. (a label already exists?)" );
		}
	}

	public void process(RebuildStep state, Instruction inst, ResolveLabel lookup) {
		/* This is a check to see if we have a 0-byte jump. If we do, let's optimize it out. That's what's going on here */
		if (CodeUtils.is(inst, "JMP rel8") || CodeUtils.is(inst, "JMP rel32")) {
			Instruction peek = state.peekNext();

			if (peek != null && hasLabel(peek)) {
				if (getJumpLabel(inst) == getLabel(peek)) {
					//CrystalUtils.print_error("Found a redundant jump");
					//CodeUtils.p(inst);
					//CodeUtils.p(peek);

					/* we're going to create an empty instruction to avoid a potential label conflict */
					program.zero_bytes();

					/* otherwise... swallow this specific jump instruction because we don't need it */
					return;
				}
			}
		}

		/* add our instruction */
		program.addInstruction( Instruction.createBranch(inst.getCode(), getJumpLabel(inst).id) );
	}
}
