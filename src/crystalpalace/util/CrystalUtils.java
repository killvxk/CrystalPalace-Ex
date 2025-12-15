package crystalpalace.util;

import java.io.*;
import java.util.*;

public class CrystalUtils {

	public static boolean isWindows() {
		String OS = System.getProperty("os.name").toLowerCase();
		return OS.indexOf("win") >= 0;
	}

	public static final void print_error(String message) {
		if (isWindows()) {
			System.out.println("[-] " + message);
		}
		else {
			System.out.println("\u001B[01;31m[-]\u001B[0m " + message);
		}
	}

	public static final void print_good(String message) {
		if (isWindows()) {
			System.out.println("[+] " + message);
		}
		else {
			System.out.println("\u001B[01;32m[+]\u001B[0m " + message);
		}
	}

	public static final void print_info(String message) {
		if (isWindows()) {
			System.out.println("[*] " + message);
		}
		else {
			System.out.println("\u001B[01;34m[*]\u001B[0m " + message);
		}
	}

	public static final void print_warn(String message) {
		if (isWindows()) {
			System.out.println("[!] " + message);
		}
		else {
			System.out.println("\u001B[01;33m[!]\u001B[0m " + message);
		}
	}

	public static final void print_stat(String message) {
		if (isWindows()) {
			System.out.println("[%] " + message);
		}
		else {
			System.out.println("\u001B[01;35m[%] "+message+"\u001B[0m");
		}
	}

	public static String toHex(long n) {
		return "0x" + Long.toHexString(n);
	}

	public static final void handleException(Throwable e) {
		System.out.println("Exception ("+Thread.currentThread().getName()+"/"+Thread.currentThread().getId()+") " + e.getClass() + ": " + e.getMessage());
		e.printStackTrace();
	}

	public static final void reportException(Throwable e) {
		print_info("Exception ("+Thread.currentThread().getName()+"/"+Thread.currentThread().getId()+") " + e.getClass() + ": " + e.getMessage());
	}

	public static int parseInt(String x, int failValue) {
		try {
			return Integer.parseInt(x);
		}
		catch (Exception ex) {
			return failValue;
		}
	}

	public static byte[] readBytes(InputStream in, int size) throws IOException {
		byte[] content = new byte[size];
		int read = in.read(content);
		in.close();
		return content;
	}

	public static byte[] readFromFile(String file) throws IOException {
		byte[] content = new byte[(int)new File(file).length()];
		InputStream in = new FileInputStream(file);
		int read = in.read(content);
		in.close();

		return content;
	}

	public static void writeToFile(String file, byte[] contents) throws IOException {
		OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
		out.write(contents);
		out.flush();
		out.close();
	}

	public static byte[] toUTF8(String content) {
		return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	public static byte[] toUTF8Z(String content) {
		content += (char)0;
		return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	public static Set toSet(String text) {
		Set temp = new LinkedHashSet();
		if ("".equals(text))
			return temp;

		String[] blah = text.split(",\\s*");
		for (int x = 0; x < blah.length; x++) {
			temp.add(blah[x].trim());
		}

		return temp;
	}

	public static List toList(String text) {
		List     temp = new LinkedList();
		if ("".equals(text))
			return temp;

		String[] blah = text.split(",\\s*");
		for (int x = 0; x < blah.length; x++) {
			temp.add(blah[x].trim());
		}

		return temp;
	}

	public static String readStringFromFile(String file) throws IOException {
		return new String(readFromFile(file), java.nio.charset.StandardCharsets.UTF_8);
	}

	public static String bytesToHex(byte[] temp) {
		LinkedList list = new LinkedList();
		for (int x = 0; x < temp.length; x++) {
			list.add( String.format("%02x", temp[x] & 0xff) );
		}

		return String.join(" ", list);
	}

	public static String bytesToHex(int[] temp) {
		LinkedList list = new LinkedList();
		for (int x = 0; x < temp.length; x++) {
			list.add( String.format("%02x", temp[x]) );
		}

		return String.join(" ", list);
	}

	public static int getDWORD(byte[] val, int loc) {
		java.nio.ByteBuffer off = java.nio.ByteBuffer.wrap(val, loc, 4);
		off.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		return off.getInt();
	}

	public static void putDWORD(byte[] val, int loc, int valz) {
		java.nio.ByteBuffer off = java.nio.ByteBuffer.wrap(val, loc, 4);
		off.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		off.putInt((int)valz);
	}

	public static byte[] hexToBytes(String temp) throws NumberFormatException {
		/* replace whitespace with nothing, to allow more readable user-specified byte arrays */
		temp = temp.replace(" ", "");

		if ((temp.length() % 2) != 0)
			throw new NumberFormatException("String length not divisible by 2");

		byte[] result = new byte[temp.length() / 2];

		for (int x = 0; x < result.length; x++) {
			String hexstr = temp.substring(x * 2, (x * 2) + 2);
			result[x] = (byte)Integer.parseInt(hexstr, 16);
		}

		return result;
	}

	public static byte[] rc4encrypt(byte[] key, byte[] val) throws Exception {
		long checksum = adler32checksum(val);

		Logger.print_stat( "rc4encrypt: " + val.length + "b hash: 0x" + Long.toString(checksum, 16) + " - " + checksum );

		javax.crypto.Cipher             rc4  = javax.crypto.Cipher.getInstance("ARCFOUR");
		javax.crypto.spec.SecretKeySpec jkey = new javax.crypto.spec.SecretKeySpec(key, "ARCFOUR");

		rc4.init(javax.crypto.Cipher.ENCRYPT_MODE, jkey);
		return rc4.doFinal(val);
	}

	public static long adler32checksum(byte[] value) {
		java.util.zip.Adler32 checksum = new java.util.zip.Adler32();
		checksum.update(value);
		return checksum.getValue();
	}

	public static byte[] toBytes(String x, String enc) {
		try {
			return x.getBytes(enc);
		}
		catch (Exception ex) {
			CrystalUtils.handleException(ex);
			return null;
		}
	}

	public static byte[] reverse(byte[] fwd) {
		byte[] rev = new byte[fwd.length];
		for (int x = 0; x < rev.length; x++) {
			rev[x] = fwd[rev.length - (x + 1)];
		}

		return rev;
	}

	public static long ROR(long hash, int bits) {
		long a = hash >>> bits;
		long b = hash << (32 - bits);
		return (a | b) & 0xFFFFFFFFL;
	}

	public static int ror13(byte[] hashme) {
		long  res = 0;

		for (int x = 0; x < hashme.length; x++) {
			res  = ROR(res, 13);
			res += hashme[x];
		}

		return (int)res;
	}

}
