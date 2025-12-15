package crystalpalace.util;

import java.io.*;
import java.util.*;
import java.nio.*;

import java.util.zip.*;

public class Packer {
	protected ByteArrayOutputStream bytes;
	protected DataOutputStream      out;

	protected byte                  bdata[] = new byte[8];
        protected ByteBuffer            buffer  = ByteBuffer.wrap(bdata);

	public void little() {
		buffer.order(ByteOrder.LITTLE_ENDIAN);
	}

	public void big() {
		buffer.order(ByteOrder.BIG_ENDIAN);
	}

	public Packer() {
		bytes  = new ByteArrayOutputStream(4096);
		out    = new DataOutputStream(bytes);
	}

	public void pad(int x) {
		try {
			byte temp[] = new byte[x];
			out.write(temp, 0, x);
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addByte(int x) {
		try {
			byte temp[] = new byte[1];
			temp[0] = (byte)x;
			out.write(temp, 0, 1);
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addBytes(byte[] x) {
		try {
			out.write(x, 0, x.length);
		}
		catch (IOException ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addInt(int x) {
		try {
			buffer.clear();
			buffer.putInt(x);
			out.write(bdata, 0, 4);
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addLong(long x) {
		try {
			buffer.clear();
			buffer.putLong(x);
			out.write(bdata, 0, 8);
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addShort(short x) {
		try {
			buffer.clear();
			buffer.putShort(x);
			out.write(bdata, 0, 2);
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addUShort(int x) {
		try {
			buffer.clear();
			buffer.putShort((short)x);
			out.write(bdata, 0, 2);
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addData(byte[] x) {
		try {
			addInt(x.length);
			out.write(x, 0, x.length);
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addDataVerify(byte[] x) {
		Adler32 checksum = new Adler32();
		checksum.update(x);

		try {
			addInt((int)checksum.getValue());

			Logger.print_stat("verify " + x.length + "b to 0x" + Integer.toString((int)checksum.getValue(), 16));

			out.write(x, 0, x.length);
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addWideString(String x) {
		try {
			x = x + (char)0;
			addData(x.getBytes("UTF-16LE"));
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public void addUTF8String(String x) {
		try {
			x = x + (char)0;
			addData(x.getBytes("UTF-8"));
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
	}

	public int size() {
		try {
			out.flush();
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
		return bytes.size();
	}

	public byte[] getBytes() {
		try {
			out.flush();
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}
		return bytes.toByteArray();
	}
}
