package crystalpalace.util;

import java.io.*;
import java.nio.*;
import java.util.*;

public class ByteWalker {
	protected Stack     states;
	protected ByteOrder order;
	protected byte[]    data;
	protected boolean   sane = true;

	public ByteWalker(byte[] data) {
		this.data = data;
		states = new Stack();
		little();
		pushState();
	}

	/* change the endianess to little */
	public void little() {
		order = ByteOrder.LITTLE_ENDIAN;
	}

	/* change the endianess to big */
	public void big() {
		order = ByteOrder.BIG_ENDIAN;
	}

	/*
	 * If we haven't had an exception, we're sane... if we have... then consider the
	 * stream of whatever we're parsing corrupted.
	 */
	public boolean isSane() {
		return sane;
	}

	public byte[] popBytes(int length) {
		try {
			if (length == 0)
				return new byte[0];

			byte[] temp = new byte[length];
			getState().readFully(temp, 0, length);
			return temp;
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.print_error("Could not pop: " + length + " bytes");
			CrystalUtils.handleException(ex);
			return new byte[0];
		}
	}

	public int popByte() {
		try {
			return getState().read();
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.print_error("Could not pop byte");
			CrystalUtils.handleException(ex);
			return 0;
		}
	}

	public void skip(int x) {
		try {
			getState().skip(x);
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.print_error("Could not skip " + x + " bytes");
			CrystalUtils.handleException(ex);
		}
	}

	public boolean isComplete() {
		try {
			return getState().available() == 0;
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.print_error("Could not assess available bytes");
			CrystalUtils.handleException(ex);
			return false;
		}
	}

	/* I appreciate the stream has mark/reset options available too. The advantage here is to allow
	   recursive jumps and restorations of previous stream state. I don't know if COFF requires this,
	   but I've found this capability handy in other parts I've written in the past */
	public void Mark() {
		try {
			GoTo(data.length - getState().available());
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.handleException(ex);
		}
	}

	public int getPosition() {
		try {
			return data.length - getState().available();
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.handleException(ex);
		}

		return -1;
	}

	/* jump to a specific pointer in our file */
	public void GoTo(int x) {
		pushState();

		try {
			getState().skip(x);
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.print_error("Could not skip " + x + " bytes as part of GoTo");
			CrystalUtils.handleException(ex);
		}
	}

	/* return from the jump */
	public void Return() {
		popState();
	}

	protected void pushState() {
		states.push( new DataInputStream(new ByteArrayInputStream(data)) );
	}

	protected void popState() {
		/*
		 *   In theory, these gymnastics are not needed as the underlying
		 *   ByteArrayInputStream's close() method does nothing.
		 */
		try {
			DataInputStream in = (DataInputStream)states.pop();
			in.close();
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.print_error("Could not pop state");
			CrystalUtils.handleException(ex);
		}
	}

	protected DataInputStream getState() {
		return (DataInputStream)states.peek();
	}

	protected ByteBuffer _getBytes(int length) {
		ByteBuffer wrapper = ByteBuffer.wrap(popBytes(length));
		wrapper.order(order);
		return wrapper;
	}

	public int readShort() {
		return Short.toUnsignedInt(_getBytes(2).getShort());
	}

	public int readInt() {
		return _getBytes(4).getInt();
	}

	public long readLong() {
		return _getBytes(8).getLong();
	}

	public byte[] getBytes(int length) {
		return popBytes(length);
	}

	public String readStringA(int length) {
		try {
			/* let's go through these gymnastics, because any null bytes we INCLUDE in our java.lang.String
			 * will throw off our expected contract for .equals(). We need to avoid those */
			byte[] data = popBytes(length);
			int    len  = 0;
			for (; len < data.length; len++)
				if (data[len] == 0)
					break;

			return new String(data, 0, len, "UTF-8");
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.print_error("Could not read string " + length + " bytes");
			CrystalUtils.handleException(ex);
		}

		return "";
	}

	public String readStringA() {
		try {
			Mark();
			int x = 0;
			while (popByte() != 0)
				x++;
			Return();

			return readStringA(x);
		}
		catch (Exception ex) {
			sane = false;
			CrystalUtils.print_error("Could not read ASCIIZ string");
			CrystalUtils.handleException(ex);
		}

		return "";
	}
}
