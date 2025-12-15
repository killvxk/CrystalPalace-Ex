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
public class DangerWalk {
	protected COFFObject          object  = null;
	protected Set                 touched = new HashSet();
	protected Map                 funcs   = null;
	protected Code                code    = null;
	protected String              start   = null;

	public DangerWalk(Code code, String start) {
		this.code   = code;
		this.object = code.getObject();
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

	/*
	 * These are the walks from the link-time optimizer, one day I'll refactor all of this into a reusable pattern/function. Not today though.
	 */
	protected void walk_x64(String function) {
		/* our instructions of interest */
		Set x64insts = new HashSet();
		x64insts.add("LEA r64, m");
		x64insts.add("MOV r64, r/m64");
		x64insts.add("CALL r/m64");

		/* if we're walking the function, it's referenced/called and we want to keep it */
		touched.add(function);

		/* start walking instruction by instruction */
		Iterator i = ( (List)funcs.get(function) ).iterator();
		while (i.hasNext()) {
			Instruction inst = (Instruction)i.next();

			if ( inst.isCallNear() ) {
				Symbol temp = code.getLabel( inst.getMemoryDisplacement32() );
				if (temp != null && !touched.contains( temp.getName() )) {
					check(function, temp.getName(), inst);
					walk_x64( temp.getName() );
				}
			}
			else if (inst.isIPRelativeMemoryOperand()) {
				if (x64insts.contains(inst.getOpCode().toInstructionString())) {
					Symbol temp = code.getLabel( inst.getMemoryDisplacement32() );
					if (temp != null && !touched.contains( temp.getName() )) {
						check(function, temp.getName(), inst);
						walk_x64( temp.getName() );
					}
				}
			}

			/* handle .refptr labels as a special case */
			Relocation r = code.getRelocation(inst);
			if (r != null && r.getSymbolName().startsWith(".refptr.")) {
				String symb = r.getSymbolName().substring(8);
				Symbol temp = object.getSymbol(symb);
				if (temp != null && ".text".equals(temp.getSection().getName()) && !touched.contains(temp.getName())) {
					check(function, temp.getName(), inst);
					walk_x64( temp.getName() );
				}
			}
		}
	}

	protected void walk_x86(String function) {
		/* if we're walking the function, it's referenced/called and we want to keep it */
		touched.add(function);

		/* start walking instruction by instruction */
		Iterator i = ( (List)funcs.get(function) ).iterator();
		while (i.hasNext()) {
			Instruction inst = (Instruction)i.next();

			/* if this is an instruction that touches our local label, we want to get that label
			 * and walk that function */
			if ( inst.isCallNear() ) {
				Symbol temp = code.getLabel( inst.getMemoryDisplacement32() );
				if (temp != null && !touched.contains( temp.getName() )) {
					check(function, temp.getName(), inst);
					walk_x86( temp.getName() );
				}
			}

			/* check for a relocation associated with the label */
			Relocation r = code.getRelocation(inst);
			if (r != null && ".text".equals(r.getSymbolName())) {
				Symbol temp = code.getLabel( r.getOffsetAsLong() );
				if (temp != null && !touched.contains( temp.getName() )) {
					check(function, temp.getName(), inst);
					walk_x86( temp.getName() );
				}
			}
			/* same type of thing as the x64 .refptr issue... we have a relocation for a local symbol... we need to walk it */
			else if (r != null) {
				Symbol temp = object.getSymbol(r.getSymbolName());
				if (temp != null && temp.getSection() != null && ".text".equals(temp.getSection().getName()) && !touched.contains(temp.getName())) {
					check(function, temp.getName(), inst);
					walk_x86( temp.getName() );
				}
			}
		}
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
