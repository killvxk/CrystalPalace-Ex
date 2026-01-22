package crystalpalace.btf.pass.mutate;

import crystalpalace.btf.*;
import crystalpalace.btf.lttl.*;
import crystalpalace.btf.Code;
import crystalpalace.btf.pass.*;
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
import com.github.icedland.iced.x86.info.*;

/* Simple re-ordering of blocks within a function. Preserves the first and last blocks */
public class BlockParty implements FilterCode {
	public BlockParty() {
	}

	public List filterCode(Rebuilder builder, String func, List instructions) {
		Blocks blocks = builder.getBlocks();

		/* do nothing if we're talking about 2 or less blocks */
		if (blocks.getBlocks(func).size() <= 2) {
			return instructions;
		}

		/* break it down now! */
		LinkedList allblocks = blocks.getBlocks(func);
		//blocks.dump(func, builder.getAnalysis(), allblocks);

		LinkedList first     = (LinkedList)allblocks.removeFirst();

		/* now let's shuffle the in-between blocks */
		Collections.shuffle(allblocks);

		/* restore our first and last elements, please! */
		allblocks.addFirst(first);

		/* OK, now let's turn all of this into something sane */
		List     all = new LinkedList();
		Iterator i   = allblocks.iterator();
		while (i.hasNext()) {
			List     block = (List)i.next();
			all.addAll(block);
		}

		return all;
	}
}
