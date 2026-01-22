package crystalpalace.btf;

import crystalpalace.btf.pass.easypic.*;
import crystalpalace.btf.pass.mutate.*;

import crystalpalace.btf.lttl.*;

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
public class Rebuilder {
	protected Code                analysis;
	protected Map                 funcs;
	protected COFFObject          object;

	protected CodeAssembler       program   = null;
	protected CodeAssemblerResult results   = null;

	protected RebuildStep         state     = new RebuildStep(this);

	/*
	 * One of the things I don't love about this scheme... I have two separate ways/places I'm
	 * tracking program labels. So long as we don't have a function as a jump target, it should
	 * be OK. I'm under the assumption anywhere we're dealing with a function pointer or a function
	 * call, we're working with a symbol within our COFF and everything else are basically local
	 * jumps that need to be handled. Relocation "labels" are shared within the jumps data structure.
	 */
	protected LocalLabels         labels    = null;
	protected Relocations         relocs    = null;
	protected Jumps               jumps     = null;
	protected Zones               zones     = null;
	protected Blocks              blocks    = null;
	protected DirtyLeaves         leaves    = null;

	public Rebuilder(Code analysis, Map funcs) {
		this.analysis = analysis;
		this.funcs    = funcs;
		this.object   = analysis.getObject();
	}

	public Blocks getBlocks() {
		return blocks;
	}

	public Code getAnalysis() {
		return analysis;
	}

	public Jumps getJumps() {
		return jumps;
	}

	public Relocations getRelocations() {
		return relocs;
	}

	public Zones getZones() {
		return zones;
	}

	public DirtyLeaves getLeaves() {
		return leaves;
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

	/*
	 * And, this is where the real work goes on.
	 */
	public COFFObject rebuild(RebuildConfig config) {
		return _rebuild(config.getAdder(), config.getLookup(), config.getFilter());
	}

	protected COFFObject _rebuild(AddInstruction adder, ResolveLabel lookup, FilterCode filter) {
		/* let's... build a new set of instructions */
		program = new CodeAssembler(object.getBits());

		labels  = new LocalLabels(analysis, program);
		relocs  = new Relocations(analysis, program);
		jumps   = new Jumps(analysis, program);
		zones   = new Zones(analysis);
		blocks  = new Blocks();
		leaves  = new DirtyLeaves();

		/*
		 * Step (1) - We need to create labels within our assembler that... later, we'll add to the
		 * instruction chain AND refer to within specific instructions.
		 */

		/* (1)(i) let's create labels for... our function symbols! */
		labels.analyze(funcs);

		/* (1)(ii) look for our relocations next */
		relocs.analyze(this, jumps);

		/* 1(iii) let's create labels for... our jump targets! */
		walk(jumps);

		/* 1(iv) figure out which zones of instructions have rflag changes that are consequential, so we know to
		 * not mutate/modify them in a way that breaks that value */
		walk(zones);

		/* 1(v) do a block analysis across the entire program. This is helpful because it lets us know which
		 * instructions are leaders and edges. */
		blocks.analyze(this, funcs);

		/* 1(vi) walk functions to determine which are leaf functions with an unaligned stack pointer (x64) */
		leaves.analyze(this, funcs);

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

			/* create a label for our function/symbol */
			labels.startFunction(entry);

			/* and, let's walk the instructions of our functions */
			ListIterator k = null;
			if (isFunction)
				k = filter.filterCode(this, (String)entry.getKey(), (List)entry.getValue()).listIterator();
			else
				k = ((List)entry.getValue()).listIterator();

			/* register which function context we're in */
			state.enter((String)entry.getKey(), k);

			while (k.hasNext()) {
				Instruction inst = (Instruction)k.next();
				state.step(inst);

				/* does this instruction have a label (e.g., relocations/jump targets) */
				jumps.handleLabel(inst);

				/* We need to set the IP of any re-used instruction to zero, for CodeAssembler to do its thing properly */
				Instruction copy = inst.copy();
				copy.setIP(0);

				/* If we're not a function, just add whatever bytes, please */
				if (!isFunction) {
					program.addInstruction(copy);
				}
				/* complicated much? If this instruction has a relocation associated with it, we're not bothered by doing nothing with it
				 * right now. We have to re-patch relocation offsets (and they're independent of our code structure) after the program
				 * is rebuilt anyways. So, we give this a pass and treat it as something we've already done something to */
				else if (analysis.hasRelocation(inst)) {
					adder.addInstruction(program, state, copy);
				}
				/* handle instructions that reference local functions and such */
				else if (labels.usesLocalLabel(inst)) {
					labels.process(state, inst, lookup);
				}
				/* And, if this is a jump targeting one of our labels, yeap... let's update that to go to the labels and not the original address */
				else if (jumps.isJump(inst)) {
					jumps.process(state, inst, lookup);
				}
				/* add our copied instruction to the (new, rebuilt) program */
				else {
					adder.addInstruction(program, state, copy);
				}

				/* If this is the end of a block (e.g., an edge), let our block tracker determine if a connective jump is helpful here */
				blocks.finalize(program, state, inst);
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
		labels.rebuild(object, results);

		/* now, let's fix... our... relocations */
		relocs.rebuild(object, results);

		return object;
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
}
