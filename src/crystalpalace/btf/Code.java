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

/*
 * Code is an intermediate artifact of our analyze, modify, assemble pipeline. This is
 * where we disassemble the .text section of a COFF and x-ref symbols and relocations
 * with the disassembled instructions. We'll need that for our manipulations later.
 *
 * We'll also use the information here, later on, to aid our re-assembly of our modified
 * program.
 */
public abstract class Code {
	protected COFFObject object;
	protected byte[]     code;
	protected List       instructions = new LinkedList();
	protected Map        labels       = new HashMap();
	protected Map        relocs       = new HashMap();
	protected Map        offsets      = new HashMap();
	protected long       startip      = 0;

	protected Code(COFFObject o) {
		this.object = o;
		this.code   = object.getSection(".text").getRawData();
	}

	/* fire it up! */
	public static Code Init(COFFObject o) {
		if ("x64".equals(o.getMachine())) {
			return new CodeX64(o);
		}
		else if ("x86".equals(o.getMachine())) {
			return new CodeX86(o);
		}

		throw new RuntimeException("Can't analyze code for " + o.getMachine());
	}

	public COFFObject getObject() {
		return object;
	}

	public byte[] getCodeAsBytes() {
		return code;
	}

	public ConstantOffsets getOffsets(Instruction instr) {
		return (ConstantOffsets)offsets.get(instr);
	}

	/* handle some disassemble */
	public abstract List disassemble();

	public Code setIP(long startip) {
		this.startip = startip;
		return this;
	}

	public Code analyze() {
		/* disassemble the .text section */
		instructions = disassemble();

		/* grab all of our labels too! */
		Iterator itz = object.getSection(".text").getSymbols().iterator();
		while (itz.hasNext()) {
			Symbol temp = (Symbol)itz.next();
			if (temp.isFunction()) {
				labels.put( temp.getValue(), temp );
			}
			else if (temp.isGlobalVariable()) {
				labels.put( temp.getValue(), temp );
			}
			else if (temp.getType() == 0 && temp.getValue() > 0) {
				throw new RuntimeException("Caught a candidate symbol, non-function/non-code: " + temp);
			}
		}

		/* AND... let's grab all of our relocations too */
		Iterator i = object.getSection(".text").getRelocations().iterator();
		while (i.hasNext()) {
			Relocation reloc = (Relocation)i.next();
			relocs.put(reloc.getVirtualAddress(), reloc );
		}

		return this;
	}

	/* determine if a specific label refers to a function or some data */
	public boolean isFunction(String name) {
		Symbol symb = object.getSymbol(name);
		if (symb == null)
			throw new RuntimeException("No symbol for " + name + " to determine func or not");
		else
			return symb.isFunction();
	}

	/* get the symbol associated with a specific address */
	public Symbol getLabel(long addr) {
		return (Symbol)labels.get(addr);
	}

	/* check if our instruction has a displacement offset in it */
	public boolean hasOffsets(Instruction instr) {
		return offsets.containsKey(instr);
	}

	/* check if there's a relocation associated with the specific instruction */
	public boolean hasRelocation(Instruction instr) {
		return getRelocation(instr) != null;
	}

	/* get the relocation associated with a specific instruction */
	public abstract Relocation getRelocation(Instruction instr);

	/* turn the relocation into a symbol result (for printing purposes) */
	public abstract SymbolResult relocationToSymbol(Relocation r, int operand, long address);

	/* map the functions in our program to their corresponding instructions */
	public Map getCodeByFunction() {
		Map rv       = new LinkedHashMap();
		List current = new LinkedList();

		Iterator i = getCode().iterator();
		while (i.hasNext()) {
			Instruction instr = (Instruction)i.next();

			if (getLabel(instr.getIP()) != null) {
				current = new LinkedList();
				rv.put(getLabel(instr.getIP()).getName(), current);
			}

			current.add(instr);
		}

		return rv;
	}

	/* get back our disassembled instructions */
	public List getCode() {
		return new LinkedList(instructions);
	}
}
