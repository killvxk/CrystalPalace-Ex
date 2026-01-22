package crystalpalace.btf.pass.mutate;

import crystalpalace.btf.*;
import crystalpalace.btf.Code;
import crystalpalace.btf.pass.*;
import crystalpalace.coff.*;
import crystalpalace.export.*;
import crystalpalace.util.*;

import java.util.*;
import java.io.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;

public class LinkTimeOptimizer extends CallWalk {
	public LinkTimeOptimizer(Code code) {
		super(code);
	}

	public void walk(String symbol) {
		if ("x64".equals(object.getMachine()))
			walk_x64(symbol);
		else
			walk_x86(symbol);
	}

	public Map apply(ExportInfo exports, Map _funcs) {
		funcs  = _funcs;

		/* find entry symbol dynamically (supports go, _go, __go with optional @N suffix) */
		String entrySymbol = COFFObject.findEntrySymbolName(funcs, object.getMachine());
		if (entrySymbol != null) {
			walk(entrySymbol);
		}

		/* walk all of our exported functions too */
		Iterator z = exports.iterator();
		while (z.hasNext()) {
			Map.Entry entry = (Map.Entry)z.next();
			walk((String)entry.getKey());
		}

		/* sanity check that we had something to start from */
		if (touched.size() == 0)
			throw new RuntimeException("+optimize requires go() function as entrypoint or 1+ exported functions.");

		/* symbol names to get rid of from our COFF */
		Set removeme = new HashSet();

		/* let's do a little *snip* *snip* for anything that's not used by our program */
		Iterator i = funcs.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry entry = (Map.Entry)i.next();

			/* if it's not a function, we're not interested in cutting it out. */
			if ( !object.getSymbol(entry.getKey().toString()).isFunction() )
				continue;

			if ( !touched.contains(entry.getKey().toString()) ) {
				i.remove();
				removeme.add(entry.getKey().toString());
				//CrystalUtils.print_error("Getting rid of: " + entry.getKey().toString());
			}
		}

		/* get that stuff out of our COFF now */
		object.removeSymbols(removeme);

		/* And, since we're optimizing the size somewhat. Let's trim any NOP and INT3 instructions padding the
		 * end of our instructions too. Eh?!? */
		Iterator j = funcs.values().iterator();
		while (j.hasNext()) {
			LinkedList instrs = (LinkedList)j.next();
			Iterator k = instrs.descendingIterator();
			while (k.hasNext()) {
				Instruction next = (Instruction)k.next();

				if (CodeUtils.is(next, "NOP") || CodeUtils.is(next, "INT3"))
					k.remove();
				else
					break;
			}
		}

		/* and as simple as that... return our modified function map */
		return funcs;
	}
}
