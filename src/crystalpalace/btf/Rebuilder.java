package crystalpalace.btf;

import crystalpalace.btf.pass.easypic.*;
import crystalpalace.btf.pass.mutate.*;

import crystalpalace.coff.*;
import crystalpalace.util.*;

import java.util.*;
import java.io.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;

/*
 * The final part of our analyze, process, rebuild pipeline. Here, we take in a series of instructions
 * and we return a patched-up and ready to use (or, re-analyze/process/rebuild) COFF.
 */
public class Rebuilder implements ResolveLabel {
	protected class RelocationFix {
		protected Relocation reloc;
		protected int        instOffset;
		protected byte[]     valueAt;
		protected CodeLabel  label;
		protected String     toStrOld;

		public RelocationFix(Relocation reloc, CodeLabel label, int instOffset) {
			this.reloc      = reloc;
			this.label      = label;
			this.instOffset = instOffset;
			this.valueAt    = object.getSection(".text").fetch((int)reloc.getVirtualAddress(), 4);
			this.toStrOld   = reloc.toString();
		}

		public Relocation getRelocation() {
			return reloc;
		}

		public CodeLabel getLabel() {
			return label;
		}

		public int getOffsetFromInstruction() {
			return instOffset;
		}

		public long getRelocOffsetLong() {
			return CrystalUtils.getDWORD(valueAt, 0);
		}

		public byte[] getRelocOffsetValue() {
			return valueAt;
		}

		public void setRelocOffsetValue(long x) {
			CrystalUtils.putDWORD(valueAt, 0, (int)x);
		}

		/* If we report an error based on this relocation, it's going to be POST-mutilation. We cache the
		   pre-mutiliation string representation to give users an error message and information they can
		   follow in the pre-mutiliated disassembly */
		public String toStringOld() {
			return toStrOld;
		}
	}

	protected Code                analysis;
	protected Map                 funcs;
	protected COFFObject          object;

	protected CodeAssembler       program   = null;
	protected CodeAssemblerResult results   = null;
	protected Map                 relocs    = new LinkedHashMap();

	protected RebuildStep         state     = new RebuildStep(this);

	/*
	 * One of the things I don't love about this scheme... I have two separate ways/places I'm
	 * tracking program labels. So long as we don't have a function as a jump target, it should
	 * be OK. I'm under the assumption anywhere we're dealing with a function pointer or a function
	 * call, we're working with a symbol within our COFF and everything else are basically local
	 * jumps that need to be handled. Relocation "labels" are shared within the jumps data structure.
	 */
	protected Map                 labels    = new HashMap();
	protected Jumps               jumps     = null;
	protected Zones               zones     = null;

	public Rebuilder(Code analysis, Map funcs) {
		this.analysis = analysis;
		this.funcs    = funcs;
		this.object   = analysis.getObject();
	}


	public RelocationFix addRelocation(Relocation r, CodeLabel label, int offset) {
		RelocationFix fix = new RelocationFix(r, label, (int)offset);
		relocs.put(r, fix);
		return fix;
	}

	public Zones getZones() {
		return zones;
	}

	protected byte[] assemble() {
		final ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
		Object result = program.assemble(new CodeWriter() {
			public void writeByte(byte value) {
				out.write(value);
			}
		//}, 0, BlockEncoderOptions.DONT_FIX_BRANCHES | BlockEncoderOptions.RETURN_NEW_INSTRUCTION_OFFSETS);
		}, 0, BlockEncoderOptions.RETURN_NEW_INSTRUCTION_OFFSETS);

		/* This is a relatively catastrophic failure, because it means one of our transformations violated something */
		if (result instanceof String) {
			throw new RuntimeException("assemble() failed: " + (String)result);
		}

		// note, result is a CodeAssemblerResult otherwise and it can give us addresses of
		// the labels we created, possibly very useful for updating relocations and such later
		results = (CodeAssemblerResult)result;

		return out.toByteArray();
	}

	/*
	 * Walk the entire program (mostly for analysis purposes)
	 */
	public void walk(CodeVisitor visitor) {
		Iterator i = funcs.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry)i.next();
			String    func  = (String)entry.getKey();
			List      insts = (List)entry.getValue();

			Iterator j = insts.iterator();
			while (j.hasNext()) {
				Instruction next = (Instruction)j.next();
				visitor.visit(next);
			}
		}
	}

	public CodeLabel getCodeLabel(RebuildStep step, String symbol) {
		return (CodeLabel)labels.get(symbol);
	}

	/*
	 * And, this is where the real work goes on.
	 */
	public COFFObject rebuild() {
		return rebuild(null, this);
	}

	public COFFObject rebuild(AddInstruction adder) {
		return rebuild(adder, this);
	}

	public COFFObject rebuild(AddInstruction adder, ResolveLabel lookup) {
		/* let's... build a new set of instructions */
		program = new CodeAssembler(object.getBits());
		jumps   = new Jumps(analysis, program);
		zones   = new Zones(analysis);

		/*
		 * Step (1) - We need to create labels within our assembler that... later, we'll add to the
		 * instruction chain AND refer to within specific instructions.
		 */

		/* (1)(i) let's create labels for... our function symbols! */
		Iterator i = funcs.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry)i.next();
			labels.put( entry.getKey(), program.createLabel() );
		}

		/* (1)(ii) look for our relocations next */
		walk(new CodeVisitor() {
			public void visit(Instruction next) {
				Relocation r = analysis.getRelocation(next);

				if (r == null)
					return;

				/* NOTE to myself: we don't care about the reloc target because we fix it after
				 * all of this re-processing is done. When we create a label, it's only to find
				 * the instruction associated with our relocation so we can update its VA after
				 * everything else is handled */

				CodeLabel label  = jumps.createLabel(next.getIP());
				long      offset = r.getVirtualAddress() - next.getIP();

				relocs.put(r, new RelocationFix(r, label, (int)offset));
			}
		});

		/* 1(iii) let's create labels for... our jump targets! */
		walk(jumps);

		/* 1(iv) figure out which zones of instructions have rflag changes that are consequential, so we know to
		 * not mutate/modify them in a way that breaks that value */
		walk(zones);

		/*
		 * Step (2) - We're going to create a List of Instructions for our to-be assembled program.
		 * This is where we change things over to labels (to get the right offsets) and we can do
		 * any modifications to specific instructions we want here too.
		 */

		Iterator j = funcs.entrySet().iterator();
		while (j.hasNext()) {
			Map.Entry entry = (Map.Entry)j.next();

			/* are we currently processing a function or some data thing */
			boolean isFunction = analysis.isFunction((String)entry.getKey());

			//if (!isFunction)
			//	CrystalUtils.print_good("Rebuilder: " + entry.getKey() + " processing as data");

			/* create a label for our function/symbol */
			program.label( (CodeLabel)labels.get(entry.getKey()) );
			program.zero_bytes();

			/* and, let's walk the instructions of our functions */
			ListIterator k = ((List)entry.getValue()).listIterator();

			/* register which function context we're in */
			state.enter((String)entry.getKey(), k);

			while (k.hasNext()) {
				Instruction inst = (Instruction)k.next();
				state.step(inst);

				/* does this instruction have a label (e.g., relocations/jump targets) */
				try {
					if (jumps.hasLabel(inst))
						program.label( jumps.getLabel(inst) );
				}
				catch (IllegalArgumentException ex) {
					throw new RuntimeException( "Can't label '" + String.format("%016X %s", inst.getIP(), inst.getOpCode().toInstructionString()) + "'. (a label already exists?)" );
				}

				/* We need to set the IP of any re-used instruction to zero, for CodeAssembler to do its thing properly */
				Instruction copy = inst.copy();
				copy.setIP(0);

				/* complicated much? If this instruction has a relocation associated with it, we're not bothered by doing nothing with it
				 * right now. We have to re-patch relocation offsets (and they're independent of our code structure) after the program
				 * is rebuilt anyways. So, we give this a pass and treat it as something we've already done something to */
				if (analysis.hasRelocation(inst)) {
					if (adder == null || !isFunction)
						program.addInstruction(copy);
					else
						adder.addInstruction(program, state, copy);
					continue;
				}
				/* we're going to swap out calls and LEAs with our new code labels, right? */
				else if ( inst.isCallNear() ) {
					Symbol temp = analysis.getLabel( inst.getMemoryDisplacement32() );
					if (temp != null) {
						program.addInstruction( Instruction.createBranch(inst.getCode(), lookup.getCodeLabel(state, temp.getName()).id) );
						continue;
					}
				}
				/* some instructions loading one of our labels into a register, I guess */
				else if (inst.isIPRelativeMemoryOperand()) {
					if ( "LEA r64, m".equals(inst.getOpCode().toInstructionString()) ) {
						Symbol temp = analysis.getLabel( inst.getMemoryDisplacement32() );
						if (temp != null) {
							//CrystalUtils.print_warn(CodeUtils.toString(inst) + " for " + temp.getName() + " @ " + CrystalUtils.toHex(inst.getMemoryDisplacement32()));
							program.lea( new AsmRegister64(new ICRegister(inst.getOp0Register())), AsmRegisters.mem_ptr(  lookup.getCodeLabel(state, temp.getName())  ));
							continue;
						}
					}
					else if ("MOV r64, r/m64".equals(inst.getOpCode().toInstructionString()) ) {
						Symbol temp = analysis.getLabel( inst.getMemoryDisplacement32() );
						if (temp != null) {
							//CrystalUtils.print_warn(CodeUtils.toString(inst) + " for " + temp.getName() + " @ " + CrystalUtils.toHex(inst.getMemoryDisplacement32()));
							program.mov( new AsmRegister64(new ICRegister(inst.getOp0Register())), AsmRegisters.mem_ptr(  lookup.getCodeLabel(state, temp.getName())  ));
							continue;
						}
					}
					else if ("CALL r/m64".equals(inst.getOpCode().toInstructionString()) ) {
						Symbol temp = analysis.getLabel( inst.getMemoryDisplacement32() );
						if (temp != null) {
							//CrystalUtils.print_warn(CodeUtils.toString(inst) + " for " + temp.getName() + " @ " + CrystalUtils.toHex(inst.getMemoryDisplacement32()));
							program.call( AsmRegisters.qword_ptr( lookup.getCodeLabel(state, temp.getName()) ));
							continue;
						}
					}
				}
				/* And, if this is a jump targeting one of our labels, yeap... let's update that to go to the labels and not the original address */
				else if (jumps.isJump(inst)) {
					program.addInstruction( Instruction.createBranch(inst.getCode(), jumps.getJumpLabel(inst).id) );
					continue;
				}

				/* this is a sanity check... our decompiler resolves all of our instructions to what they actually refer to. If we failed to
				 * catch an instruction that's EIP/RIP relative and modify it to a label, this newly built program will crash. Better we catch
				 * this and give feedback vs. let it continue on like all is cool. */
				if (inst.isIPRelativeMemoryOperand()) {
					throw new RuntimeException( "Can't transform '" + String.format("%016X %s", inst.getIP(), inst.getOpCode().toInstructionString()) + "'. (Modified program will crash)" );
				}

				/* add our copied instruction to the (new, rebuilt) program */
				if (adder == null || !isFunction)
					program.addInstruction(copy);
				else
					adder.addInstruction(program, state, copy);
			}
		}

		/*
		 * Step (3) - let's reassemble the whole thing
		 */
		byte[] text_content = assemble();

		/*
		 * Step (4) - let's update our COFF with everything
		 */
		object.getSection(".text").setData(text_content);

		/* fix our symbols */
		Iterator l = object.getSection(".text").getSymbols().iterator();
		while (l.hasNext()) {
			Symbol temp = (Symbol)l.next();
			if (labels.containsKey(temp.getName())) {
				//CrystalUtils.print_warn("Keeping   " + temp.getName());
				temp.setValue( results.getLabelRIP( (CodeLabel)labels.get(temp.getName()) ) );
			}
		}

		/* now, let's fix... our... relocations */
		List     newrelocs = new LinkedList();

		Iterator m = relocs.values().iterator();
		while (m.hasNext()) {
			RelocationFix entry = (RelocationFix)m.next();
			CodeLabel     label = entry.getLabel();
			Relocation    reloc = entry.getRelocation();

			/* We need to set the new VA of where this relocation is applied to, because of our LTO (or other mods)
			 * this has almost certainly shifted */
			reloc.setVirtualAddress( results.getLabelRIP(label) + entry.getOffsetFromInstruction() );

			/* We're not done! If we're on x86, we need to resolve the symbol associated with the (old) offset and then
			 * update the offset to the symbol's new home */
			if (".text".equals(reloc.getSymbolName())) {
				Symbol temp = analysis.getLabel(entry.getRelocOffsetLong());

				if (temp != null)
					entry.setRelocOffsetValue(temp.getValue());
				else
					throw new RuntimeException("Could not fix " + entry.toStringOld() + " - no symbol at " + CrystalUtils.toHex(entry.getRelocOffsetLong()) + ". (Modified program will crash)");
			}

			/* And... uhhh... lucky us... the disassembler doesn't preserve the indirect (e.g., from the current IP)
			 * offset when it disassembles. Rather, it preserves the final address it thinks is being pointed at. So
			 * to make our relocations work, we need to re-apply that original offset */
			object.getSection(".text").patch((int)reloc.getVirtualAddress(), entry.getRelocOffsetValue());

			newrelocs.add(reloc);
		}

		/* And, we want to replace all the old relocs, with our fixed ones, to get rid of any relocs associated with a
		 * function we removed/optimized out */
		object.getSection(".text").setRelocations(newrelocs);

		return object;
	}
}
