package crystalpalace.btf.pass.mutate;

import crystalpalace.btf.*;
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

public class FunctionDisco {
	protected COFFObject          object  = null;
	protected Map                 funcs   = null;
	protected Code                code    = null;

	public FunctionDisco(Code code) {
		this.code   = code;
		this.object = code.getObject();
	}

	/* Remember: we may have .text section data labels in our code, so we
	 * count functions this way. */
	public int countFunctions(Map funcs) {
		int count = 0;

		Iterator i = funcs.keySet().iterator();
		while (i.hasNext()) {
			String func = (String)i.next();
			if (code.isFunction(func))
				count++;
		}

		return count;
	}

	public Map apply(boolean preserveFirst, Map funcs) {
		/* no functions? NO DISCO! */
		if (countFunctions(funcs) <= 1)
			return funcs;

		/* put it all back together! */
		Map results = new LinkedHashMap();

		/* handle our first function, if that's desired */
		if (preserveFirst) {
			String first = (String)funcs.keySet().iterator().next();
			results.put(first, funcs.get(first));
			funcs.remove(first);
		}

		/* randomize our functions ! */
		LinkedList labels = new LinkedList(funcs.keySet());
		Collections.shuffle(labels);

		/* shift through the (randomized) list, making sure whatever is first
		 * is an actual function. If preserveFirst is set, obviously don't do it.
		 *
		 * WHY do we do this? Because my code treats symbols NOT at position 0 and
		 * not functions as inline data. So, if we have a non-func symbol end up at
		 * position 0--my processing will cease to treat it as existeing and that
		 * messes everything up for reloc processing.
		 */
		while (!preserveFirst) {
			String func = (String)labels.peekFirst();
			if (code.isFunction(func))
				break;

			labels.add( labels.removeFirst() );
		}

		/* build our new (randomized) function map */
		Iterator i = labels.iterator();
		while (i.hasNext()) {
			String name = (String)i.next();
			results.put(name, funcs.get(name));
		}

		return results;
	}
}
