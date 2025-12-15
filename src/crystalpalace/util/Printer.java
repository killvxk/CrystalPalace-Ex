package crystalpalace.util;

import java.util.*;

public class Printer {
	protected StringBuffer buffer = new StringBuffer(8192);
	protected Stack        frame  = new Stack();

	public Printer() {
		frame.push("");
	}

	protected String getPrefix() {
		return (String)frame.peek();
	}

	public void pop() {
		frame.pop();
	}

	public String decorate(String title) {
		switch (frame.size()) {
			case 1:
				return title + "\n";
			case 2:
				return title + "\n";
			case 3:
				return title + "\n";
			default:
				return title + "\n";
		}
	}

	public void push(String title) {
		buffer.append(getPrefix() + decorate(title));
		frame.push(   getPrefix() + "   ");
	}

	public void print(String x) {
		buffer.append(getPrefix() + x + "\n");
	}

	public void print(String key, long val, String x) {
		print(key, val + " (" + CrystalUtils.toHex(val) + ") " + x);
	}

	public void print(String key, long val) {
		if ("Type".equals(key)) {
			if (val >= 0 && val <= 9) {
				print(key, val + "");
			}
			else {
				print(key, val + " (" + CrystalUtils.toHex(val) + ")");
			}
		}
		else if ("SizeOfRawData".equals(key)) {
			print(key, val + "");
		}
		else if ("StorageClass".equals(key)) {
			print(key, val + "");
		}
		else if (key.endsWith(".HashCode")) {
			print(key, CrystalUtils.toHex(val));
		}
		else {
			print(key, val + " (" + CrystalUtils.toHex(val) + ")");
		}
	}

	public void print(String key, int val) {
		print(key, (long)val);
	}

	public void print(String key, String val) {
		buffer.append(getPrefix());

		key += ": ";

		while (key.length() < 17) {
			key += " ";
		}

		buffer.append(key + val + "\n");
	}

	public String toString() {
		return buffer.toString();
	}

	public void printSTDOUT() {
		System.out.println(toString());
	}
}
