package crystalpalace.pe;

import crystalpalace.coff.*;
import crystalpalace.btf.*;
import crystalpalace.export.*;
import crystalpalace.util.*;
import java.util.*;

public class OptionalHeader {
	protected ByteWalker           walker;

	public static final int IMAGE_NT_OPTIONAL_HDR32_MAGIC = 0x10b;
	public static final int IMAGE_NT_OPTIONAL_HDR64_MAGIC = 0x20b;

	protected int                  Magic;
	protected long                 AddressOfEntryPoint;
	protected long                 ImageBase;
	protected long                 SizeOfImage;
	protected long                 NumberOfRvaAndSizes;
	protected ImageDataDirectory[] DataDirectory;

	public static final int IMAGE_DIRECTORY_ENTRY_EXPORT         = 0;
	public static final int IMAGE_DIRECTORY_ENTRY_IMPORT         = 1;
	public static final int IMAGE_DIRECTORY_ENTRY_RESOURCE       = 2;
	public static final int IMAGE_DIRECTORY_ENTRY_EXCEPTION      = 3;
	public static final int IMAGE_DIRECTORY_ENTRY_SECURITY       = 4;
	public static final int IMAGE_DIRECTORY_ENTRY_BASERELOC      = 5;
	public static final int IMAGE_DIRECTORY_ENTRY_DEBUG          = 6;
	public static final int IMAGE_DIRECTORY_ENTRY_ARCHITECTURE   = 7;
	public static final int IMAGE_DIRECTORY_ENTRY_GLOBALPTR      = 8;
	public static final int IMAGE_DIRECTORY_ENTRY_TLS            = 9;
	public static final int IMAGE_DIRECTORY_ENTRY_LOAD_CONFIG    = 10;
	public static final int IMAGE_DIRECTORY_ENTRY_BOUND_IMPORT   = 11;
	public static final int IMAGE_DIRECTORY_ENTRY_IAT            = 12;
	public static final int IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT   = 13;
	public static final int IMAGE_DIRECTORY_ENTRY_COM_DESCRIPTOR = 14;

	public boolean is64() {
		return Magic == IMAGE_NT_OPTIONAL_HDR64_MAGIC;
	}

	public long getSizeOfImage() {
		return SizeOfImage;
	}

	public long getImageBase() {
		return ImageBase;
	}

	public long getAddressOfEntryPoint() {
		return AddressOfEntryPoint;
	}

	public ImageDataDirectory getDataDirectory(int x) {
		return DataDirectory[x];
	}

	public class ImageDataDirectory {
		protected long VirtualAddress;
		protected long Size;

		public ImageDataDirectory() {
			VirtualAddress = walker.readInt();
			Size           = walker.readInt();
		}

		public String toString() {
			return CrystalUtils.toHex(VirtualAddress) + " (" + Size + "b)";
		}

		public long getVirtualAddress() {
			return VirtualAddress;
		}

		public long getSize() {
			return Size;
		}
	}

	public void parse64() {
		/* MajorLinkerVersion */
		walker.skip(14);

		AddressOfEntryPoint = walker.readInt();

		/* BaseOfCode */
		walker.skip(4);

		/* ImageBase */
		ImageBase = walker.readLong();

		/* SectionAlignment and ... */
		walker.skip(24);

		SizeOfImage = walker.readInt();

		/* SizeOfHeaders */
		walker.skip(12);

		/* SizeOfStackReserve and ... */
		walker.skip(32);

		/* LoaderFlags */
		walker.skip(4);

		NumberOfRvaAndSizes = walker.readInt();
	}

	public void parse32() {
		/* MajorLinkerVersion */
		walker.skip(14);

		AddressOfEntryPoint = walker.readInt();

		/* BaseOfCode and BaseOfData */
		walker.skip(8);

		/* ImageBase */
		ImageBase = walker.readInt();

		/* SectionAlignment and ... */
		walker.skip(24);

		SizeOfImage = walker.readInt();

		/* SizeOfHeaders */
		walker.skip(12);

		/* SizeOfStackReserve and ... */
		walker.skip(16);

		/* LoaderFlags */
		walker.skip(4);

		NumberOfRvaAndSizes = walker.readInt();
	}

	public OptionalHeader(byte[] content) {
		walker = new ByteWalker(content);

		Magic = walker.readShort();

		if (Magic == IMAGE_NT_OPTIONAL_HDR32_MAGIC) {
			parse32();
		}
		else if (Magic == IMAGE_NT_OPTIONAL_HDR64_MAGIC) {
			parse64();
		}
		else {
			throw new RuntimeException("Optional Header starts with invalid Magic value " + CrystalUtils.toHex(Magic));
		}

		/* setup our data directory */
		DataDirectory = new ImageDataDirectory[(int)NumberOfRvaAndSizes];

		/* parse our ImageDataDirectory values */
		for (int x = 0; x < NumberOfRvaAndSizes; x++) {
			DataDirectory[x] = new ImageDataDirectory();
		}
	}
}
