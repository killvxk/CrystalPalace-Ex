package crystalpalace.btf.pass.easypic;

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

/*
 * What is the danger walk? It's called... I care about footguns! So, dprintf is this awesome capability... but it's
 * subversively dangerous in a PIC context. It uses OutputDebugStringA which throws exceptions to reach a handler for
 * well... proper handling. This can be OK as exceptions bubble up, but in a PIC context--we can break the ability for
 * Windows to figure out where an exception handler is and lead to all kinds of unpredictable crashes. Specifically,
 * calling OutputDebugStringA or DbgPrint from a context where we're using a helper function (and ergo, mucking with the
 * stack in ways that make our function not safe for exceptions to bubble up) can lead to unpredictable crashes. So
 * the purpose of the danger walk is to just validate that dprintf is NOT called from a dfr, fixbss, etc. symbol. That's
 * it.
 */
public class DangerWalk extends CallWalk {
	protected String              start   = null;

	public DangerWalk(Code code, String start) {
		super(code);
		this.start  = start;
	}

	public void check(String parent, String symbol, Instruction inst) {
		if ("x64".equals(object.getMachine())) {
			if ("dprintf".equals(symbol))
				throw new RuntimeException("Don't call dprintf from dfr/fixptrs/fixbss. OutputDebugStringA's message propagation (SEHs) can corrupt from these contexts. (" + start + " -> " + parent + ")");
		}
		else {
			if ("_dprintf".equals(symbol))
				throw new RuntimeException("Don't call dprintf from dfr/fixptrs/fixbss. OutputDebugStringA's message propagation (SEHs) can corrupt from these contexts. (" + start + " -> " + parent + ")");
		}

	//	CrystalUtils.print_stat(parent + " called " + symbol + " at " + String.format("%016X %s", inst.getIP(), inst.getOpCode().toInstructionString()));
	}

	public Map apply(Map _funcs) {
		funcs  = _funcs;

		if ("x64".equals(object.getMachine())) {
			walk_x64(start);
		}
		else {
			walk_x86(start);
		}

		/* and as simple as that... return our modified function map */
		return funcs;
	}
}
