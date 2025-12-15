package crystalpalace.export;

import java.util.*;
import crystalpalace.spec.TagStore;

/* keep track of PICO exported functions. We do double duty here. We need a local (this object) list of
 * exports, but a global list of tags */
public class Exports implements ExportInfo {
	protected ExportObject object;
	protected Set          exports = new HashSet();

	/* this is a global that maintains our tag mappings */
	protected TagStore     tags;

	public Exports(ExportObject object, TagStore tags) {
		this.object = object;
		this.tags   = tags;
	}

	public TagStore getTags() {
		return tags;
	}

	/* mark a PICO function as for export */
	public void export(String function, String symbol) {
		object.check(function);
		exports.add(function);
		tags.register(function, symbol, object.x64());
	}

	public Iterator iterator() {
		Map values = new HashMap();

		Iterator i = exports.iterator();
		while (i.hasNext()) {
			String next = (String)i.next();
			values.put(next, tags.getFunctionTag(next).getTag());
		}

		return values.entrySet().iterator();
	}
}
