package crystalpalace.btf;

import crystalpalace.btf.pass.easypic.*;
import crystalpalace.btf.pass.mutate.*;

import crystalpalace.btf.lttl.*;

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
 * Configure the specifics of our rebuild pipeline.
 */
public class RebuildConfig {
	protected AddInstruction  adder  = null;
	protected ResolveLabel    lookup = null;
	protected FilterCode      filter = null;

	public RebuildConfig() {
		PassThrough defaultv = new PassThrough();
		this.adder  = defaultv;
		this.lookup = defaultv;
		this.filter = defaultv;
	}

	/*
	 * Our opportunity to modify individual instructions
	 */
	public RebuildConfig adder(AddInstruction adder) {
		this.adder = adder;
		return this;
	}

	/*
	 * Our opportunity to switch labels around
	 */
	public RebuildConfig lookup(ResolveLabel lookup) {
		this.lookup = lookup;
		return this;
	}

	/*
	 * Our opportunity to change instruction order or act on a sequence of instructions
	 */
	public RebuildConfig filter(FilterCode filter) {
		this.filter = filter;
		return this;
	}

	public AddInstruction getAdder() {
		return adder;
	}

	public ResolveLabel getLookup() {
		return lookup;
	}

	public FilterCode getFilter() {
		return filter;
	}
}
