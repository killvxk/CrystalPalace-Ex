package crystalpalace.pe;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import java.util.*;

/*
 * This is not a full PE object, but rather enough to extract the "Machine" type and
 * do some VERY light sanity checking that we (probably) have a valid PE file.
 */
public class PEObjectSimple {
	protected int     Machine;
	protected boolean valid = true;
	protected String  lastError = "";

	public PEObjectSimple(byte[] data) {
		this(new ByteWalker(data));
	}

	public PEObjectSimple(ByteWalker walker) {
		int Magic = walker.readShort();
		if (Magic != 0x5a4d) {
			throw new RuntimeException("Invalid DLL: File header is not 'MZ'");
		}

		/* skip the next 58 bytes of the DOS Header */
		walker.skip(58);

		/* read in e_lfanew */
		long e_lfanew = walker.readInt();

		/* now, let's go there */
		walker.GoTo((int)e_lfanew);

		/* read in the next header */
		long pe_signature = walker.readInt();
		if (pe_signature != 0x00004550L) {
			throw new RuntimeException("Invalid DLL: PE signature is not 'PE'\\x00\\x00");
		}

		/* AND, we read in a COFF now (or, in this case, the one part we care about) */
		Machine = walker.readShort();

		/* reset it all? */
		walker.Return();
	}

	/* And, this is all I care about, the ability to parse out the target machine of a DLL, so we can select
	 * the right target in our .spec file. I don't want the user to have to bother specifying this value */
	public String getMachine() {
		switch (Machine) {
			case 0x8664:
				return "x64";
			case 0x14c:
				return "x86";
			case 0xaa64:
				return "arm64";
			default:
				return "Unknown " + CrystalUtils.toHex(Machine);
		}
	}

	public String toString() {
		return getMachine() + " PE";
	}
}
