package crystalpalace.spec;

import crystalpalace.coff.*;
import crystalpalace.util.*;
import crystalpalace.export.*;

import java.util.*;
import java.io.*;

public class SpecObject {
	public static final int BYTES  = 0;
	public static final int OBJECT = 1;

	protected Object       value;
	protected SpecProgram  owner;
	protected int          type;
	protected String       source;

	public SpecObject(SpecProgram owner, byte[] b, String source) {
		this.value  = b;
		this.type   = BYTES;
		this.owner  = owner;
		this.source = source;
	}

	public SpecObject(SpecProgram owner, ExportObject obj, String source) {
		this.value  = obj;
		this.type   = OBJECT;
		this.owner  = owner;
		this.source = source;
	}

	public String getSource() {
		return source;
	}

	public byte[] getBytes() throws SpecProgramException {
		if (type == BYTES)
			return (byte[])value;

		throw new SpecProgramException(owner, "POP expected BYTES, received " + toString());
	}

	public ExportObject getObject() throws SpecProgramException {
		if (type == OBJECT)
			return (ExportObject)value;

		throw new SpecProgramException(owner, "POP expected OBJECT, received " + toString());
	}

	public String toString() {
		if (type == BYTES) {
			return source + " as byte[" + ((byte[])value).length + "]";
		}
		else {
			return source + " as " + value.getClass().getSimpleName();
		}
	}
}
