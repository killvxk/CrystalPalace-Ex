package crystalpalace.spec;

import crystalpalace.coff.*;
import crystalpalace.pe.*;
import crystalpalace.util.*;
import crystalpalace.export.*;

import java.util.*;
import java.util.zip.*;
import java.io.*;

/**
 * A class to parse a DLL or COFF Object and extract API/{@code link} arguments from it.
 * <p>
 *
 * Construct this object with {@link #None}, {@link #Parse}, {@link #ParseDll}, or {@link #ParseObject}.
 * <p>
 *
 * Use this with {@link LinkSpec#run(Capability, Map)} and {@link LinkSpec#runConfig}.
 */
public class Capability {
	/** The name of the $KEY for this capability in our environment */
	protected String key;

	/** The contents of the capability */
	protected byte[] contents;

	/** the capability-specific program label (e.g., x64.o) */
	protected String label;

	/** the architecture of the capability (e.g., x86, x64) */
	protected String arch;

	/**
	 * Internal constructor to create a Capability. Use {@link #Parse}, {@link #ParseDll}, or {@link #ParseObject} to construct this object.
	 *
	 * @param key the $KEY for our capability
	 * @param contents the contents (raw data) of our capability
	 * @param label the preferred .spec labl for our capability
	 * @param arch the CPU arch (e.g., x86, x64) of our capability
	 */
	protected Capability(String key, byte[] contents, String label, String arch) {
		this.key      = key;
		this.contents = contents;
		this.label    = label;
		this.arch     = arch;
	}

	/**
	 * Get the $KEY used for this specific capability.
	 *
	 * @return $OBJECT if the capability is COFF, $DLL if the capability is a DLL.
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Get the contents of this capability
	 *
	 * @return the capability contents saved into this object
	 */
	public byte[] getContents() {
		return contents;
	}

	/**
	 * Get the preferred Crystal Palace target label for this capability (e.g., x64.dll)
	 *
	 * @return the target label string for this capability
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Get the CPU arch (e.g., x86, x64) of our capability
	 *
	 * @return the String x86 or x64.
	 */
	public String getArch() {
		return arch;
	}

	/**
	 * Check if this Capability is associated with a DLL or COFF.
	 *
	 * @return false if this object was created with {@link #None}
	 */
	public boolean hasCapability() {
		return key != null;
	}

	/**
	 * Is the capability a COFF?
	 *
	 * @return true if the capability is a COFF.
	 */
	public boolean isObject() {
		return "$OBJECT".equals(key);
	}

	/**
	 * Is the capability a DLL?
	 *
	 * @return true if the capability is a DLL.
	 */
	public boolean isDll() {
		return "$DLL".equals(key);
	}

	/**
	 * Create a Capability object, usable with the {@link LinkSpec} API, but not associated with a DLL or Object.
	 *
	 * @param archOrLabel the architecture target of our program (e.g., x86, x64) OR the full label name (e.g., foo.x64)
	 *
	 * @return a Capability object.
	 */
	public static Capability None(String archOrLabel) {
		String arch = "";

		if (CrystalUtils.toSet("x86, x64").contains(archOrLabel))
			arch = archOrLabel;
		else if (archOrLabel.endsWith(".x86"))
			arch = "x86";
		else if (archOrLabel.endsWith(".x64"))
			arch = "x64";
		else
			throw new RuntimeException("Label " + archOrLabel + " must end with .x86/.x64 or be x86/x64");

		return new Capability(null, new byte[0], archOrLabel, arch);
	}

	/**
	 * Create a new Capability from a COFF Object
	 *
	 * @param object the contents of the COFF file.
	 *
	 * @return a Capability object.
	 */
	public static Capability ParseObject(byte[] object) {
		COFFObject coff  = null;

		try {
			coff = new COFFParser().parse(object).getObject();
		}
                catch (RuntimeException rex) {
                        throw new RuntimeException("Invalid Object: " + rex.getMessage());
                }

		return new Capability("$OBJECT", object, coff.getMachine() + ".o", coff.getMachine());
	}

	/**
	 * Create a new Capability from a DLL
	 *
	 * @param dll the contents of the DLL file.
	 *
	 * @return a Capability object.
	 */
	public static Capability ParseDll(byte[] dll) {
		PEObjectSimple obj = new PEObjectSimple(dll);
		return new Capability("$DLL", dll, obj.getMachine() + ".dll", obj.getMachine());
	}

	/**
	 * Create a new Capability by guessing if the capability content is a DLL or Object.
	 *
	 * @param capability a Win32 DLL or COFF. If it's a DLL, {@link #ParseDll} is called. If it's a COFF, {@link #ParseObject}
         *       is called.
	 *
	 * @return a Capability object.
	 *
	 * @throws RuntimeException if the capability is not a COFF or DLL
	 */
	public static Capability Parse(byte[] capability) {
		ByteWalker peek = new ByteWalker(capability);
		int        magic = peek.readShort();

		/* MZ header, indicating a DLL */
		if (magic == 0x5a4d) {
			return ParseDll(capability);
		}
		/* x64 and x86 Machine values from COFF */
		else if (magic == 0x8664 || magic == 0x14c) {
			return ParseObject(capability);
		}
		/* we don't know what it is, punt */
		else {
			throw new RuntimeException("Argument is not a COFF or DLL.");
		}
	}

	/** Get the string representation of our capability */
	public String toString() {
		return "Capability " + arch + " " + key + " (label: " + label + ")";
	}
}
