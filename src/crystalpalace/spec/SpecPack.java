package crystalpalace.spec;

import crystalpalace.util.*;
import java.util.*;
import java.math.BigInteger;

public class SpecPack {
	protected SpecProgram program;
	protected String      format;

	public SpecPack(SpecProgram program, String format) {
		this.program = program;
		this.format  = format;
	}

	private static class Walker {
		public static final int OP_APPEND_DATA        = 0;
		public static final int OP_APPEND_LENGTH_DATA = 1;
		public static final int OP_ALIGN_NATURAL      = 2;
		public static final int OP_ALIGN_4            = 3;
		public static final int OP_ALIGN_8            = 4;

		/* current state to make accessible to consumer of this class */
		protected char    template;
		protected int     op;
		protected String  arg;

		/* internal state */
		protected String  format;
		protected String  args[];
		protected int     x;
		protected int     z;
		protected int     start;

		public Walker(String format, String args[], int argStart) {
			this.format = format;
			this.args   = args;
			this.x      = 0;
			this.z      = argStart;
			this.start  = argStart;
		}

		public int getOp() {
			return op;
		}

		public char getTemplate() {
			return template;
		}

		public String getArg() {
			return arg;
		}

		protected boolean nextAlign() {
			if (x >= format.length())
				throw new RuntimeException("pack: @ character is missing alignment argument (e.g., @4, @8, @n)");

			template = '@';

			switch (format.charAt(x)) {
				case 'n':
					op = OP_ALIGN_NATURAL;
					x++;
					return true;
				case '4':
					op = OP_ALIGN_4;
					x++;
					return true;
				case '8':
					op = OP_ALIGN_8;
					x++;
					return true;
				default:
					throw new RuntimeException("pack: invalid alignment '@" + format.charAt(x) + "'. Use @4, @8, or @n");
			}
		}

		public boolean next() {
			op  = OP_APPEND_DATA;
			arg = null;

			for (;x < format.length(); x++) {
				switch (format.charAt(x)) {
					case '@':
						x++;
						return nextAlign();
					case '#':				/* prepend our length to our template char */
						op = OP_APPEND_LENGTH_DATA;
						break;
					case 'b':				/* byte[] drawing on $VAR */
					case 'l':				/* long (8 bytes) */
					case 'i':				/* int (4 bytes) */
					case 's':				/* short (2 bytes) */
					case 'p':				/* pointer value (x86=4 bytes, x64=8 bytes) */
					case 'z':				/* NULL-terminated UTF-8LE string */
					case 'Z':				/* NULL-terminated UTF-16LE string */
						template = format.charAt(x);
						if (z < args.length) {
							arg = args[z];
							z++;
						}
						else {
							throw new RuntimeException("pack: no argument for " + format.charAt(x) + " at position " + x);
						}
						x++;
						return true;
					case 'x':				/* NULL byte */
						template = format.charAt(x);
						x++;
						return true;
					case ' ':
						break;
					default:
						throw new RuntimeException("pack: unknown template " + format.charAt(x) + " at position " + x);
				}
			}

			if (z < args.length)
				throw new RuntimeException("pack: format string " + format + " only consumed " + (z - start) + " of " + (args.length - start) + " arguments");

			return false;
		}
	}

	protected java.math.BigInteger decodeNumber(String _val, int maxb) {
		boolean negate = false;
		int     radix  = 10;

		String val = _val;

		/* check for +/- */
		if (val.startsWith("-")) {
			negate = true;
			val = val.substring(1);
		}
		else if (val.startsWith("+")) {
			val = val.substring(1);
		}

		/* check for radix */
		if (val.startsWith("0x")) {
			radix = 16;
			val   = val.substring(2);
		}
		else if (val.startsWith("0")) {
			radix = 8;
			val   = val.substring(1);
		}
		else if (val.startsWith("#")) {
			radix = 16;
			val   = val.substring(1);
		}

		/* do the conversion */
		BigInteger number = null;
		int        needs;
		try {
			number = new java.math.BigInteger(val, radix);
			needs  = number.bitLength();
		}
		catch (NumberFormatException nex) {
			throw new RuntimeException("Can't decode " + _val + " as base " + radix + " number");
		}

		/* handle negatives */
		if (negate) {
			number = number.negate();
		}

		/* check that this number is sane and in range */
		if (needs > maxb)
			throw new RuntimeException("Number " + _val + " (base " + radix + ") needs " + needs + " bits, max " + maxb);

		return number;
	}

	public byte[] processArg(Walker walker, Map env) throws SpecProgramException {
		Packer packer = new Packer();
		packer.little();

		switch (walker.getTemplate()) {
			case '@':
				return new byte[0];
			case 'x':
				return new byte[1];
			case 'b':
				return program.getFromEnv(env, walker.getArg());
			case 's':
				packer.addShort( decodeNumber(walker.getArg(), 16).shortValue() );
				return packer.getBytes();
			case 'i':
				packer.addInt( decodeNumber(walker.getArg(), 32).intValue() );
				return packer.getBytes();
			case 'l':
				packer.addLong( decodeNumber(walker.getArg(), 64).longValue() );
				return packer.getBytes();
			case 'p':
				if ("x64".equals(program.ltarch))
					packer.addLong( decodeNumber(walker.getArg(), 64).longValue() );
				else
					packer.addInt( decodeNumber(walker.getArg(), 32).intValue() );
				return packer.getBytes();
			case 'z':
				return CrystalUtils.toBytes(walker.getArg() + (char)0, "UTF-8");
			case 'Z':
				return CrystalUtils.toBytes(walker.getArg() + (char)0, "UTF-16LE");
		}

		throw new RuntimeException("pack: unimplemented templated char " + walker.getTemplate());
	}

	public byte[] getLength(byte[] arg) {
		Packer packer = new Packer();
		packer.little();
		packer.addInt(arg.length);
		return packer.getBytes();
	}

	public byte[] apply(String[] args, int argStart, Map env) throws SpecProgramException {
		Concat result = new Concat();

		Walker walker = new Walker(format, args, argStart);
		while (walker.next()) {
			/* get the raw bytes of our argument */
			byte[] data = processArg(walker, env);

			/* apply the right op */
			switch (walker.getOp()) {
				case Walker.OP_ALIGN_NATURAL:
					if ("x64".equals(program.ltarch))
						result.align(8);
					else
						result.align(4);
					break;
				case Walker.OP_ALIGN_4:
					result.align(4);
					break;
				case Walker.OP_ALIGN_8:
					result.align(8);
					break;
				case Walker.OP_APPEND_DATA:
					result.add(data);
					break;
				case Walker.OP_APPEND_LENGTH_DATA:
					result.add(getLength(data));
					result.add(data);
					break;

			}
		}

		return result.get();
	}
}
