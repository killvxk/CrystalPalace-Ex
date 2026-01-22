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
 * Allow multiple code filters to exist in a single BTF pass
 */
public class MultiFilter implements FilterCode {
	protected List passes = new LinkedList();

	public MultiFilter() {
	}

	public int size() {
		return passes.size();
	}

	public void add(FilterCode next) {
		passes.add(next);
	}

	public List filterCode(Rebuilder builder, String func, List instructions) {
		Iterator i = passes.iterator();

		while (i.hasNext()) {
			FilterCode filter = (FilterCode)i.next();
			instructions = filter.filterCode(builder, func, instructions);
		}

		return instructions;
	}
}
