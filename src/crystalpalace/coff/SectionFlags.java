package crystalpalace.coff;

import crystalpalace.util.*;

public class SectionFlags {
	protected long flags;

	public static final long IMAGE_SCN_CNT_CODE               = 0x00000020L;
	public static final long IMAGE_SCN_CNT_INITIALIZED_DATA   = 0x00000040L;
	public static final long IMAGE_SCN_CNT_UNINITIALIZED_DATA = 0x00000080L;
	public static final long IMAGE_SCN_LNK_COMDAT             = 0x00001000L;
	public static final long IMAGE_SCN_MEM_EXECUTE            = 0x20000000L;
	public static final long IMAGE_SCN_MEM_READ               = 0x40000000L;
	public static final long IMAGE_SCN_MEM_WRITE              = 0x80000000L;

	public static long getFlags(String section) {
		if (".text".equals(section)) {
			return IMAGE_SCN_CNT_CODE | IMAGE_SCN_MEM_EXECUTE | IMAGE_SCN_MEM_READ;
		}
		else if (".rdata".equals(section)) {
			return IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_READ;
		}
		else if (".data".equals(section)) {
			return IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_READ | IMAGE_SCN_MEM_WRITE;
		}
		else if (".bss".equals(section)) {
			return IMAGE_SCN_CNT_UNINITIALIZED_DATA | IMAGE_SCN_MEM_READ | IMAGE_SCN_MEM_WRITE;
		}

		/* default is the same as .rdata */
		return IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_READ;
	}

	private static boolean isSet(long flags, long x) {
		return (flags & x) == x;
	}

	public static boolean isUninitialized(long flags) {
		return isSet(flags, IMAGE_SCN_CNT_UNINITIALIZED_DATA);
	}

	public static boolean isCommonData(long flags) {
		return isSet(flags, IMAGE_SCN_LNK_COMDAT);
	}

	public static boolean hasCode(long flags) {
		return isSet(flags, IMAGE_SCN_CNT_CODE);
	}

	public static boolean isRead(long flags) {
		return isSet(flags, IMAGE_SCN_MEM_READ);
	}

	public static boolean isWrite(long flags) {
		return isSet(flags, IMAGE_SCN_MEM_WRITE);
	}

	public static boolean isExecute(long flags) {
		return isSet(flags, IMAGE_SCN_MEM_EXECUTE);
	}

	public static String toString(long flags) {
		StringBuffer s = new StringBuffer();

		s.append(isRead(flags)    ? "r" : "-");
		s.append(isWrite(flags)   ? "w" : "-");
		s.append(isExecute(flags) ? "x" : "-");

		s.append(" ");

		if (isSet(flags, IMAGE_SCN_CNT_CODE)) {
			s.append("(code)");
			s.append(" ");
		}

		if (isSet(flags, IMAGE_SCN_CNT_INITIALIZED_DATA)) {
			s.append("(init)");
			s.append(" ");
		}

		if (isSet(flags, IMAGE_SCN_CNT_UNINITIALIZED_DATA)) {
			s.append("(not init)");
			s.append(" ");
		}

		if (isCommonData(flags)) {
			s.append("(COMDAT)");
			s.append(" ");
		}

		s.append(CrystalUtils.toHex(flags));
		return s.toString();
	}
}
