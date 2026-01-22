package crystalpalace.btf.lttl;

import crystalpalace.btf.*;
import crystalpalace.btf.lttl.*;
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
 * Break our function down into a bunch of individual blocks
 */
public class Blocks {
	protected Map         groups  = new HashMap();
	protected Map         edges   = new HashMap();
	protected Jumps       jumps   = null;
	protected Rebuilder   builder = null;

	protected class BlockGroup {
		protected Map         blocks = new LinkedHashMap();
		protected LinkedList  block  = null;
		protected Instruction last   = null;

		public void leader(Instruction inst) {
			if (last != null) {
				edges.put( last.getIP(), jumps.createLabel(inst.getIP()) );
			}

			block = new LinkedList();
			add(inst);
			blocks.put(inst.getIP(), block);
		}

		public void add(Instruction inst) {
			last = inst;
			block.add(inst);
		}

		public BlockGroup(List instructions) {
			Iterator i = instructions.iterator();
			while (i.hasNext()) {
				Instruction inst = (Instruction)i.next();

				/* first instruction in the function */
				if (block == null) {
					leader(inst);
				}
				/* don't ever treat a relocation as a leader because this will interfere
				 * with our PIC helpers (which do register labels, like they are jump
				 * targets) */
				else if (builder.getAnalysis().getRelocation(inst) != null) {
					add(inst);
				}
				/* target of a jump? */
				else if (jumps.hasLabel(inst)) {
					leader(inst);
				}
				/* nada? */
				else {
					add(inst);
				}

				/* any instruction following a jump is a block too */
				if (jumps.isJump(inst) && i.hasNext()) {
					inst = (Instruction)i.next();
					leader(inst);
				}
			}
		}

		public List getBlocks() {
			return new LinkedList(blocks.values());
		}
	}

	public Map getEdges() {
		return edges;
	}

	public boolean isEdge(Instruction inst) {
		return edges.containsKey(inst.getIP());
	}

	public CodeLabel getEdgeTarget(Instruction inst) {
		return (CodeLabel)edges.get(inst.getIP());
	}

	public LinkedList getBlocks(String func) {
		return new LinkedList(((BlockGroup)groups.get(func)).getBlocks());
	}

	public List getPrologue(String func) {
		LinkedList blocks = getBlocks(func);
		if (blocks.size() > 0)
			return (List)blocks.getFirst();

		return new LinkedList();
	}

	public Map getAllBlocks() {
		Map rv = new HashMap();

		Iterator i = groups.keySet().iterator();
		while (i.hasNext()) {
			String key = (String)i.next();
			rv.put(key, getBlocks(key));
		}

		return rv;
	}

	public Blocks() {
	}

	public void dump(String func, Code analysis, List blocks) {
		CrystalUtils.print_good("BEGIN " + func);
		Iterator i = blocks.iterator();
		while (i.hasNext()) {
			List next = (List)i.next();
			CrystalUtils.print_warn("--- BLOCK ---");
			CodeUtils.print(analysis, next);
		}
		CrystalUtils.print_good("END " + func);
	}

	public void analyze(Rebuilder builder, Map funcs) {
		this.jumps   = builder.getJumps();
		this.builder = builder;

		Iterator i = funcs.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry        = (Map.Entry)i.next();
			String    name         = (String)entry.getKey();
			List      instructions = (List)entry.getValue();

			if (builder.getAnalysis().isFunction(name))
				groups.put(name, new BlockGroup(instructions));
		}
	}

	public void finalize(CodeAssembler program, RebuildStep step, Instruction current) {
		/* If the instruction is a JMP... we always do nothing, because it's guaranteed to branch
		 * somewhere and a linkage to the next block isn't our problem. */
		if (CodeUtils.is(current, "JMP rel32") || CodeUtils.is(current, "JMP rel8"))
			return;

		/* nothing to do if we're not the end of a block */
		if (!isEdge(current))
			return;

		/* If the instruction is anything else, CHECK if the next instruction is what we expect
		 * to see. If it is? Do nothing! */
		Instruction next = step.peekNext();
		if (next != null && jumps.getLabel(next) == getEdgeTarget(current)) {
			//CrystalUtils.print_good("SWALLOWED a jmp");
			return;
		}

		/* If the instruction isn't what we expect to see, toss in a hard-coded jump */
		program.jmp(getEdgeTarget(current));
	}
}
