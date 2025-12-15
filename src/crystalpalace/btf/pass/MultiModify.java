package crystalpalace.btf.pass;

import crystalpalace.btf.*;

import java.util.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;

/*
 * This is how we group our instruction modifiers to allow them to co-exist into an individual BTF pass
 */
public class MultiModify implements AddInstruction {
	protected List passes = new LinkedList();

	public MultiModify() {
	}

	public void add(BaseModify next) {
		passes.add(next);
	}

	public void addInstruction(CodeAssembler program, RebuildStep step, Instruction next) {
		BaseModify handler = null;

		/* walk each of our instruction modification passes and see if one applies. We have a built-in check to validate the pass contract that
		 * the potential modifications are exclusive of eachother. As soon as we have modifications that step on eachother, we must put them
		 * into separate BTF passes. This is my way of keeping the code honest over time */
		Iterator i = passes.iterator();
		while (i.hasNext()) {
			BaseModify temp = (BaseModify)i.next();

			if (temp.shouldModify(step, next)) {
				if (handler != null)
					throw new RuntimeException(handler.getClass().getSimpleName() + ", " + temp.getClass().getSimpleName() + " both want to modify: " + step.getInstructionString());

				handler = temp;
			}
		}

		/* if we have a handler... let it act */
		if (handler != null)
			handler.modify(program, step, next);
		/* or just add an instruction. */
		else
			program.addInstruction(next);
	}
}
