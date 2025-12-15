package crystalpalace.util;

import java.io.*;
import java.util.*;

/* a singleton for sanely managing our logging and other misc output */
public class Logger {
	protected static Logger instance = null;

	protected boolean verbose = false;

	protected Logger() {
		if ("true".equals(System.getProperty("crystalpalace.verbose", "false")))
			setVerbose(true);
		else
			setVerbose(false);
	}


	public static Logger getInstance() {
		synchronized (Logger.class) {
			if (instance == null)
 				instance = new Logger();
		}

		return instance;
	}

	public void setVerbose(boolean verb) {
		synchronized (this) {
			verbose = verb;
		}
	}

	public boolean isVerbose() {
		return verbose;
	}

	public static final void handleException(Throwable e) {
		System.out.println("Exception ("+Thread.currentThread().getName()+"/"+Thread.currentThread().getId()+") " + e.getClass() + ": " + e.getMessage());
		e.printStackTrace();
	}

	public static final void reportException(Throwable e) {
		print_info("Exception ("+Thread.currentThread().getName()+"/"+Thread.currentThread().getId()+") " + e.getClass() + ": " + e.getMessage());
	}

	public static final void print_error(String message) {
		if (CrystalUtils.isWindows()) {
			System.out.println("[-] " + message);
		}
		else {
			System.out.println("\u001B[01;31m[-]\u001B[0m " + message);
		}
	}

	public static final void println(String message) {
		if (!getInstance().isVerbose())
			return;

		System.out.println(message);
	}

	public static final void print_good(String message) {
		if (!getInstance().isVerbose())
			return;

		if (CrystalUtils.isWindows()) {
			System.out.println("[+] " + message);
		}
		else {
			System.out.println("\u001B[01;32m[+]\u001B[0m " + message);
		}
	}

	public static final void print_info(String message) {
		if (!getInstance().isVerbose())
			return;

		if (CrystalUtils.isWindows()) {
			System.out.println("[*] " + message);
		}
		else {
			System.out.println("\u001B[01;34m[*]\u001B[0m " + message);
		}
	}

	public static final void print_warn(String message) {
		if (!getInstance().isVerbose())
			return;

		if (CrystalUtils.isWindows()) {
			System.out.println("[!] " + message);
		}
		else {
			System.out.println("\u001B[01;33m[!]\u001B[0m " + message);
		}
	}

	public static final void print_stat(String message) {
		if (!getInstance().isVerbose())
			return;

		if (CrystalUtils.isWindows()) {
			System.out.println("[%] " + message);
		}
		else {
			System.out.println("\u001B[01;35m[%] "+message+"\u001B[0m");
		}
	}
}
