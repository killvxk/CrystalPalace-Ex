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

public class GoFirst {
	protected COFFObject          object  = null;
	protected Code                code    = null;

	public GoFirst(Code code) {
		this.code   = code;
		this.object = code.getObject();
	}

	public Map apply(Map funcs) {
		/* find entry symbol dynamically (supports go, _go, __go with optional @N suffix) */
		String symbol = COFFObject.findEntrySymbolName(funcs, object.getMachine());

		if (symbol == null)
			throw new RuntimeException("+gofirst requires go() function as entrypoint");

		Map results = new LinkedHashMap();

		/* add our symbol to results, remove from our existing function map */
		results.put(symbol, funcs.remove(symbol));

		/* add everything else back to our function map */
		results.putAll(funcs);

		/* and as simple as that... return our modified function map */
		return results;
	}
}
