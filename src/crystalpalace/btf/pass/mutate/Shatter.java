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

/* scramble blocks across the entire program */
public class Shatter implements FilterCode {
	protected Map iterators = null;

	public Shatter() {
	}

	/*
	 * Setup all of our stuff
	 */
	protected void setup(Rebuilder builder) {
		iterators = new HashMap();

		/* first pass! */
		Map  firsts = new HashMap();
		List rest   = new LinkedList();

		Iterator i = builder.getBlocks().getAllBlocks().entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry  entry     = (Map.Entry)i.next();
			String     func      = (String)entry.getKey();
			LinkedList allblocks = (LinkedList)entry.getValue();

			/* separate first blocks from the rest */
			List first = (List)allblocks.removeFirst();

			/* save the rest of our blocks for distribution later */
			rest.addAll(allblocks);

			/* save our first block (prologue) and associate it with its function */
			firsts.put(func, first);
		}

		/* let's stick our various prologues into an array so we can do O(1) accesses */
		ArrayList myvals = new ArrayList((Collection)firsts.values());

		/* shuffle the rest of our blocks, please */
		Collections.shuffle(rest);

		/* second pass, let's distribute the blocks across our functions */
		Iterator j = rest.iterator();
		for (int x = 0; j.hasNext(); x++) {
			List next = (List)j.next();
			List home = (List)myvals.get(x % myvals.size());
			home.addAll(next);
		}

		/* third pass! create our store our iterators now */
		Iterator k = firsts.entrySet().iterator();
		while (k.hasNext()) {
			Map.Entry entry  = (Map.Entry)k.next();
			String    func   = (String)entry.getKey();
			List      instrs = (List)entry.getValue();
			iterators.put( func, instrs );
		}
	}

	public List filterCode(Rebuilder builder, String func, List instructions) {
		if (iterators == null) {
			setup(builder);
		}
		return (List)iterators.get(func);
	}
}
