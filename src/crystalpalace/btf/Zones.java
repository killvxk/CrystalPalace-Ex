package crystalpalace.btf;

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

/* keep track of which instruction zones we can't modify eflags/rflags within */
public class Zones implements CodeVisitor {
	protected Set   danger = new HashSet();
	protected Code  analysis;
	protected List  zone = new LinkedList();
	protected Set   ignore = new HashSet();

	public Zones(Code analysis) {
		this.analysis = analysis;

		ignore.add("CMP EAX, imm32"); /* fixptrs acts on this, but in a flags-preserving way */
	}

	public boolean isDangerous(Instruction inst) {
		return danger.contains(inst.getIP());
	}

	/* instructions that I know Crystal Palace doesn't cause trouble with */
	public boolean isSafe(Instruction inst) {
		return ignore.contains(inst.getOpCode().toInstructionString());
	}

	/* build up our knowledge of the code and the jump targets */
	public void visit(Instruction next) {
		/* reset our danger zone! */
		if (next.getRflagsModified() != 0) {
			zone = new LinkedList();
		}
		else if (next.getRepPrefix() || next.getRepnePrefix()) {
			/* these are our REP instructions, treat them like normal instructions for now. We're not going to mutate
			 * or otherwise modify CLD/STD or something with a REP prefix anyways */
		}
		else if (next.getRflagsRead() != 0) {
			Iterator i = zone.iterator();
			while (i.hasNext()) {
				Instruction temp = (Instruction)i.next();
				danger.add(temp.getIP());
			}
		}

		if (!isSafe(next))
			zone.add(next);
	}
}
