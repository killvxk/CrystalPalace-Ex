package crystalpalace.pe;

import crystalpalace.btf.*;
import crystalpalace.coff.*;
import crystalpalace.export.*;
import crystalpalace.util.*;
import java.util.*;

/* an incomplete PE walker, we're mainly interested in the import table */
public class PEWalker implements COFFVisitor {
	protected ByteWalker     walker;
	protected COFFObject     object;
	protected OptionalHeader ntheaders;
	protected byte[]         image;
	protected String         arch;

	public PEWalker() {
	}

	public void visit(COFFWalker.Header header) {
	}

	/* populate our virtual layout DLL */
	public void visit(COFFWalker.Section section) {
		byte[] src    = section.getRawData();
		int    dstIdx = (int)section.getVirtualAddress();

		System.arraycopy(src, 0, image, dstIdx, src.length);
	}

	public void visit(COFFWalker.Symbol symbol) {
	}

	public void visit(COFFWalker.Relocation reloc) {
	}

	/* build up our virtual layout DLL so we can just walk it for parsing other things */
	public void visitOH(byte[] data) {
		ntheaders = new OptionalHeader(data);
		image     = new byte[(int)ntheaders.getSizeOfImage()];
	}

	public class ImageThunkData {
		public static final long IMAGE_ORDINAL_FLAG = 0x80000000L;

		protected int    address;
		protected String function;
		protected int    ordinal;

		public ImageThunkData(ByteWalker impwalker) {
			/* this is where the pointer would get patched */
			address = impwalker.getPosition();

			/* let's further dig into the IMAGE_IMPORT_BY_NAME which is at this offset */
			long funcRVA;

			if (ntheaders.is64()) {
				funcRVA = impwalker.readInt();
				impwalker.skip(4);
			}
			else {
				funcRVA = impwalker.readInt();
			}

			if (funcRVA == 0) {
				address  = 0;
				function = null;
				ordinal  = 0;
				return;
			}

			if ((funcRVA & IMAGE_ORDINAL_FLAG) == IMAGE_ORDINAL_FLAG) {
				function = null;
				ordinal  = (int)funcRVA & 0xFFFF;
			}
			else {
				impwalker.GoTo((int)funcRVA);
				ordinal  = impwalker.readShort();
				function = impwalker.readStringA();
				impwalker.Return();
			}
		}

		public boolean isValid() {
			return address != 0;
		}

		public int getAddress() {
			return address;
		}

		public String getName() {
			return function;
		}

		public int getOrdinal() {
			return ordinal;
		}
	}

	public class ImageImportDescriptor {
		protected long    OriginalFirstThunk;
		protected long    TimeDateStamp;
		protected long    ForwarderChain;
		protected long    NameRVA;
		protected long    FirstThunk;
		protected String  NameStr;
		protected List    thunks = new LinkedList();

		public ImageImportDescriptor(ByteWalker impwalker) {
			OriginalFirstThunk = impwalker.readInt();
			TimeDateStamp      = impwalker.readInt();
			ForwarderChain     = impwalker.readInt();
			NameRVA            = impwalker.readInt();
			FirstThunk         = impwalker.readInt();

			if (NameRVA == 0)
				return;

			/* read in our library name string */
			impwalker.GoTo((int)NameRVA);
			NameStr = impwalker.readStringA();
			impwalker.Return();

			/* start loading our thunks */
			impwalker.GoTo((int)FirstThunk);
			while (true) {
				ImageThunkData thunk = new ImageThunkData(impwalker);
				if (!thunk.isValid())
					break;

				thunks.add(thunk);
			}
			impwalker.Return();
		}

		public List getThunks() {
			return thunks;
		}

		public boolean isValid() {
			return NameRVA != 0;
		}

		public String getName() {
			return NameStr;
		}
	}

	public void processImports(PEVisitor visitor) {
		/* let's get the import table and process that */
		OptionalHeader.ImageDataDirectory dir = ntheaders.getDataDirectory(OptionalHeader.IMAGE_DIRECTORY_ENTRY_IMPORT);

		ByteWalker impwalker = new ByteWalker(image);
		impwalker.GoTo((int)dir.getVirtualAddress());

		while (true) {
			ImageImportDescriptor lib = new ImageImportDescriptor(impwalker);
			if (!lib.isValid())
				break;

			Iterator i = lib.getThunks().iterator();
			while (i.hasNext()) {
				visitor.visit(lib, (ImageThunkData)i.next());
			}
		}
	}

	public void start() {
		/* start walking this data structure! */
		int Magic = walker.readShort();
		if (Magic != 0x5a4d)
			throw new RuntimeException("File header is not MZ");

		/* skip the next 58 bytes of the DOS Header */
		walker.skip(58);

		/* read in e_lfanew */
		long e_lfanew = walker.readInt();

		/* now, let's go there */
		walker.GoTo((int)e_lfanew);

		/* read in the next header */
		long pe_signature = walker.readInt();
		if (pe_signature != 0x00004550L)
			throw new RuntimeException("PE signature is not 'PE'\\x00\\x00");

		/* Let's parse the embedded COFF side */

		//
		// Here, we could create a COFFParser and get a COFFObject with our sections and such. I'm not doing it
		// because it's more that can go wrong later (we just need the import table). There's also a duplicate
		// symbol issue in DLLs (.file, for example) that I don't feel like sorting right now in the current
		// COFF parser. Instead, we're just going to read the Machine value and move on.
		//
		// new COFFParser().parse(walker).getObject();

		int machine = walker.readShort();
		if (machine == 0x8664)
			arch = "x64";
		else if (machine == 0x14c)
			arch = "x86";
		else if (machine == 0xaa64)
			arch = "arm64";
		else
			throw new RuntimeException("Unknown machine for DLL " + CrystalUtils.toHex(machine));

		/* reset it all? */
		walker.Return();
		walker.GoTo((int)e_lfanew);
		walker.skip(4);

		/* now we walk the COFF again, but this time to get some added details our COFF parser wouldn't get */
		new COFFWalker().walk(walker, this);

		/* reset it again */
		walker.Return();
		walker.GoTo((int)e_lfanew);
		walker.skip(4);
	}

	public void walk(byte[] content, PEVisitor visitor) {
		/* we want our byte walker to be an instance var, we need it */
		this.walker  = new ByteWalker(content);

		/* walk our PE header and parts within */
		start();

		/* pass on our arch value to the whole thing */
		visitor.visit(arch);

		/* We're going to process the import table */
		processImports(visitor);

		/* process our NT Headers */
		visitor.visit(ntheaders);
	}
}
