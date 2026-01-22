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
 * Determine if our function is a dirty leaf or not. A dirty leaf is a function whose stack is not
 * aligned because the compiler decided it's not necessary in that function's context.
 */
public class DirtyLeaves {
	protected Set dirty = new HashSet();

	public DirtyLeaves() {
	}

	public void walk(Blocks blocks, String name, List instructions) {
		/* Step 1... walk the function and determine if we are a leaf or not. If we make a call, we're not a leaf.
		 * Assume the stack is properly aligned and we're done */
		Iterator i = instructions.iterator();
		while (i.hasNext()) {
			Instruction next = (Instruction)i.next();
			if (next.isCallNear() || next.isCallNearIndirect() || next.isCallFar() || next.isCallFarIndirect())
				return;
		}

		//CrystalUtils.print_info("LEAF: " + name);

		/* Step 2... so... we're a leaf! Fantastic. Let's walk the stack ops in the first block and determine if
		 * the function is aligned or not */
		int stackptr = 8;

		/* Here, we're going to measure the stack depth of the first block only. If it's not aligned, the function
		 * is not in an aligned state. */
		Iterator j = blocks.getPrologue(name).iterator();
		while (j.hasNext()) {
			Instruction next = (Instruction)j.next();

			if (next.getStackPointerIncrement() < 0)
				stackptr += next.getStackPointerIncrement();
		}

		if ((stackptr % 16) != 0) {
			//CrystalUtils.print_error(name + " is DIRTY");
			dirty.add(name);
		}
	}

	public boolean isDirty(String name) {
		return dirty.contains(name);
	}

	public void analyze(Rebuilder builder, Map funcs) {
		/* this is an x64 analysis only */
		if (!builder.getAnalysis().getObject().x64())
			return;

		Iterator i = funcs.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry        = (Map.Entry)i.next();
			String    name         = (String)entry.getKey();
			List      instructions = (List)entry.getValue();

			if (builder.getAnalysis().isFunction(name))
				walk(builder.getBlocks(), name, instructions);

			//if (isDirty(name))
			//	CodeUtils.print(builder.getAnalysis(), builder.getBlocks().getPrologue(name));
		}
	}
}
