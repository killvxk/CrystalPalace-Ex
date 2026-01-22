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

/*
 * This is our analyze/process/rebuild pipeline for instructions that interact with named parts of
 * our .text section (e.g., local functions, data embedded in .text)
 */
public class LocalLabels {
	protected CodeAssembler program;
	protected Code          analysis;
	protected Map           labels = new HashMap();

	public LocalLabels(Code analysis, CodeAssembler program) {
		this.analysis = analysis;
		this.program  = program;
	}

	public CodeLabel getLabel(String func) {
		return (CodeLabel)labels.get(func);
	}

	/* this is our analysis step */
	public void analyze(Map funcs) {
		Iterator i = funcs.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry)i.next();
			labels.put( entry.getKey(), program.createLabel() );
		}
	}

	/* call this at the beginning of each function to put down its label, thanks */
	public void startFunction(Map.Entry entry) {
		program.label( (CodeLabel)labels.get(entry.getKey()) );
		program.zero_bytes();
	}

	public boolean usesLocalLabel(Instruction inst) {
		if (inst.isCallNear())
			return true;

		if (inst.isIPRelativeMemoryOperand())
			return true;


		if (CodeUtils.is(inst, "JMP rel8") || CodeUtils.is(inst, "JMP rel32"))
			return analysis.getLabel(inst.getMemoryDisplacement32()) != null;;

		return false;
	}

	public void process(RebuildStep state, Instruction inst, ResolveLabel lookup) {
		Symbol temp = analysis.getLabel( inst.getMemoryDisplacement32() );
		if (temp == null) {
			/* this is a sanity check... our decompiler resolves all of our instructions to what they actually refer to. If we failed to
			 * catch an instruction that's EIP/RIP relative and modify it to a label, this newly built program will crash. Better we catch
			 * this and give feedback vs. let it continue on like all is cool. */
			throw new RuntimeException( "Can't transform '" + state.getInstructionString() + "'. (Modified program will crash)" );
		}

		/* we're going to swap out calls and LEAs with our new code labels, right? */
		if ( inst.isCallNear() ) {
			program.addInstruction( Instruction.createBranch(inst.getCode(), lookup.getCodeLabel(state, temp.getName()).id) );
		}
		/* handle jumps to a function (-O2/-Os) */
		else if (CodeUtils.is(inst, "JMP rel8") || CodeUtils.is(inst, "JMP rel32")) {
			program.jmp( lookup.getCodeLabel(state, temp.getName()) );
		}
		/* some instructions loading one of our labels into a register, I guess */
		else if (inst.isIPRelativeMemoryOperand()) {
			if ( "LEA r64, m".equals(inst.getOpCode().toInstructionString()) ) {
				//CrystalUtils.print_warn(CodeUtils.toString(inst) + " for " + temp.getName() + " @ " + CrystalUtils.toHex(inst.getMemoryDisplacement32()));
				program.lea( new AsmRegister64(new ICRegister(inst.getOp0Register())), AsmRegisters.mem_ptr(  lookup.getCodeLabel(state, temp.getName())  ));
			}
			else if ("MOV r64, r/m64".equals(inst.getOpCode().toInstructionString()) ) {
				//CrystalUtils.print_warn(CodeUtils.toString(inst) + " for " + temp.getName() + " @ " + CrystalUtils.toHex(inst.getMemoryDisplacement32()));
				program.mov( new AsmRegister64(new ICRegister(inst.getOp0Register())), AsmRegisters.mem_ptr(  lookup.getCodeLabel(state, temp.getName())  ));
			}
			else if ("CALL r/m64".equals(inst.getOpCode().toInstructionString()) ) {
				//CrystalUtils.print_warn(CodeUtils.toString(inst) + " for " + temp.getName() + " @ " + CrystalUtils.toHex(inst.getMemoryDisplacement32()));
				program.call( AsmRegisters.qword_ptr( lookup.getCodeLabel(state, temp.getName()) ));
			}
			else {
				throw new RuntimeException( "Can't transform '" + state.getInstructionString() + "' for " + temp + ". (Modified program will crash)" );
			}
		}
	}

	public void rebuild(COFFObject object, CodeAssemblerResult results) {
		Iterator l = object.getSection(".text").getSymbols().iterator();
		while (l.hasNext()) {
			Symbol temp = (Symbol)l.next();
			if (labels.containsKey(temp.getName())) {
				//CrystalUtils.print_warn("Keeping   " + temp.getName());
				temp.setValue( results.getLabelRIP( (CodeLabel)labels.get(temp.getName()) ) );
			}
		}
	}
}
