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
public class Relocations {
	protected CodeAssembler program;
	protected Code          analysis;
	protected Map           relocs = new LinkedHashMap();

	public Relocations(Code analysis, CodeAssembler program) {
		this.analysis = analysis;
		this.program  = program;
	}

	public RelocationFix get(Relocation r) {
		return (RelocationFix)relocs.get(r);
	}

	public RelocationFix add(Relocation r, CodeLabel label, int offset) {
		RelocationFix fix = new RelocationFix(analysis.getObject(), r, label, (int)offset);
		relocs.put(r, fix);
		return fix;
	}

	public void resolve(Relocation r) {
		relocs.remove(r);
	}

	public void analyze(Rebuilder rebuilder, final Jumps jumps) {
		rebuilder.walk(new CodeVisitor() {
			public void visit(Instruction next) {
				Relocation r = analysis.getRelocation(next);

				if (r == null)
					return;

				/* NOTE to myself: we don't care about the reloc target because we fix it after
				 * all of this re-processing is done. When we create a label, it's only to find
				 * the instruction associated with our relocation so we can update its VA after
				 * everything else is handled */

				CodeLabel     label  = jumps.createLabel(next.getIP());
				long          offset = r.getVirtualAddress() - next.getIP();

				RelocationFix fix    = add(r, label, (int)offset);

				/*
				 * This is kind of a hack. What's happening is that sometimes the compiler generates
				 * an encoding with a redundant prefix that gets lost when we're re-encoding. This
				 * messes up our relocation. I expect JMP r/m64 to be 6b, so I'm setting the post
				 * encoded offset now. This is DEFINITELY a hack. The right answer is to determine
				 * reloc by reloc if it's at the displacementOffset, immediateOffset, etc. and to
				 * query the post-eencode associated instruction and match everything up. Let's see
				 * how well this hack holds for now.
				 */
				if (CodeUtils.is(next, "JMP r/m64"))
					fix.instOffset = 2;
			}
		});
	}

	public void rebuild(COFFObject object, CodeAssemblerResult results) {
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
	}
}
