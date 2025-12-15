package crystalpalace.util;

import java.util.*;

public class Concat {
	protected List values = new LinkedList();
	protected int  total  = 0;
	protected int  pad    = 0;

	public Concat() {
	}

	public Concat(byte[] x) {
		add(x);
	}

	public Concat add(byte[] x) {
		values.add(x);
		total += x.length;
		return this;
	}

	public void pad(int x) {
		pad += x;
	}

	public void align(int x) {
		if ((length() % x) == 0)
			return;

		add(new byte[x - (length() % x)]);
	}

	public int length() {
		return total + pad;
	}

	public byte[] get() {
		byte[] result = new byte[total];
		int    offset = 0;

		Iterator i = values.iterator();
		while (i.hasNext()) {
			byte[] blob = (byte[])i.next();
			for (int x = 0; x < blob.length; x++) {
				result[offset + x] = blob[x];
			}
			offset += blob.length;
		}

		return result;
	}
}
