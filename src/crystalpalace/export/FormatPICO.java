package crystalpalace.export;

import crystalpalace.coff.*;
import crystalpalace.util.*;

public class FormatPICO {
	protected Concat program = new Concat();

	public static final int PICO_INST_COMPLETE   =  0x0;
	public static final int PICO_INST_PATCH      =  0x1;
	public static final int PICO_INST_COPY       =  0x2;
	public static final int PICO_INST_LL         =  0x3;
	public static final int PICO_INST_GPA        =  0x4;
	public static final int PICO_INST_PATCH_DIFF =  0x5;
	public static final int PICO_INST_PATCH_FUNC =  0x6;
	public static final int PICO_INST_EXPORT     =  0x7;

	public static final int PICO_OPT_NONE        =  0x0;

	public static final int PICO_PATCH_TEXT_TEXT =  0x0;
	public static final int PICO_PATCH_TEXT_BASE =  0x1;
	public static final int PICO_PATCH_BASE_TEXT =  0x2;
	public static final int PICO_PATCH_BASE_BASE =  0x3;

	public static final int PICO_PATCHF_FUNC     =  0x0;

	public static final int PICO_CONTEXT_CODE    =  0x5;
	public static final int PICO_CONTEXT_DATA    =  0x6;

	public FormatPICO() {
	}

        private void p(String x) {
		Logger.println(x);
        }

	/*	typedef struct {
	 *	        short type;
	 *	        short option;
	 *		int   length;
	 *	} PICO_DIRECTIVE_HDR;
	 */
	protected byte[] header(int type, int option, byte[] data) {
		Packer packer = new Packer();
                packer.little();
		packer.addByte(type);
		packer.addByte(option);
		packer.addShort((short) (4 + data.length) );

		return packer.getBytes();
	}

	protected void push(int type, int option, byte[] data) {
		program.add(header(type, option, data));
		program.add(data);
	}

	public void Complete() {
                p("COMPLETE");
		push(PICO_INST_COMPLETE, PICO_OPT_NONE, new byte[0]);
	}

	public void CopyCode(int src, int dst, int length) {
		Copy(PICO_CONTEXT_CODE, src, dst, length);
	}

	public void CopyData(int src, int dst, int length) {
		Copy(PICO_CONTEXT_DATA, src, dst, length);
	}

	/*	typedef struct {
	 *		PICO_DIRECTIVE_HDR hdr;
	 *		int src_offset;
	 *		int dst_offset;
	 *		int total;
	 *	} PICO_DIRECTIVE_COPY;
	 */
	public void Copy(int ctx, int src, int dst, int length) {
		Packer packer = new Packer();
                packer.little();
		packer.addInt(src);
		packer.addInt(dst);
		packer.addInt(length);

		p("COPY " + (ctx == 0 ? "CODE" : "DATA") + " src: " + src + ", dst: " + dst + ", len: " + length);
		push(PICO_INST_COPY, ctx, packer.getBytes());
	}

	/*	typedef struct {
	 *		PICO_DIRECTIVE_HDR hdr;
	 *		int tag;
	 *		int offset;
	 *	} PICO_DIRECTIVE_EXPORT
	 */
	public void Export(int tag, int offset) {
		Packer packer = new Packer();
                packer.little();
		packer.addInt(tag);
		packer.addInt(offset);

		p("EXPORT tag " + tag + " at offset " + offset);
		push(PICO_INST_EXPORT, PICO_OPT_NONE, packer.getBytes());
	}

	/* 	typedef struct {
	 *		PICO_DIRECTIVE_HDR hdr;
	 *		int offset;
	 * 	} PICO_DIRECTIVE_PATCH;
	 */
	public void Patch(int type, int opt, int dst) {
		Packer packer = new Packer();
                packer.little();
		packer.addInt(dst);

		push(type, opt, packer.getBytes());
	}

	public void PatchTextText(int dst) {
                p("PATCH (TEXT) TEXT " + dst + " " + CrystalUtils.toHex(dst));
		Patch(PICO_INST_PATCH, PICO_PATCH_TEXT_TEXT, dst);
	}

	public void PatchTextBase(int dst) {
                p("PATCH (TEXT) BASE " + dst + " " + CrystalUtils.toHex(dst));
		Patch(PICO_INST_PATCH, PICO_PATCH_TEXT_BASE, dst);
	}

	public void PatchBaseText(int dst) {
                p("PATCH (BASE) TEXT " + dst + " " + CrystalUtils.toHex(dst));
		Patch(PICO_INST_PATCH, PICO_PATCH_BASE_TEXT, dst);
	}

	public void PatchBaseBase(int dst) {
                p("PATCH (BASE) BASE " + dst + " " + CrystalUtils.toHex(dst));
		Patch(PICO_INST_PATCH, PICO_PATCH_BASE_BASE, dst);
	}

	public void PatchBaseDiff(int dst) {
		p("PATCH BASE DIFF " + dst + " " + CrystalUtils.toHex(dst));
		Patch(PICO_INST_PATCH_DIFF, PICO_OPT_NONE, dst);
	}

	public void PatchFunction(int dst) {
                p("PATCH FUNC " + dst + " " + CrystalUtils.toHex(dst) + " (address)");
		Patch(PICO_INST_PATCH_FUNC, PICO_PATCHF_FUNC, dst);
	}

	public void PatchImport(int dst, int index) {
		p("PATCH FUNC " + dst + " " + CrystalUtils.toHex(dst) + " index " + index);
		Patch(PICO_INST_PATCH_FUNC, index + 1, dst);
	}

	protected byte[] toBytesZ(String val) {
		try {
			val = val + (char)0;
			return val.getBytes("UTF-8");
		}
		catch (Exception ioex) {
			CrystalUtils.handleException(ioex);
		}

		return new byte[0];
	}

	public void GetProcAddress(String func) {
                p("GETPROCADDRESS " + func);
		push(PICO_INST_GPA, PICO_OPT_NONE, toBytesZ(func));
	}

	public void LoadLibrary(String mod) {
                p("LOADLIBRARY " + mod);
		push(PICO_INST_LL, PICO_OPT_NONE, toBytesZ(mod));
	}

	public byte[] getBytes() {
		return program.get();
	}
}
