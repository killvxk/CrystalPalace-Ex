# Crystal Palace

Crystal Palace is a linker and linker script language specialized to the needs of writing position-independent DLL and COFF loaders.

https://tradecraftgarden.org

## Build

Crystal Palace is written in Java and requires Apache Ant to build. The build output is in the `build/` folder.

```bash
ant clean && ant
```

Software to demonstrate Crystal Palace outputs is included in the `demo/` folder. Build these files with:

```bash
cd demo
make clean && make
```

## Environment

Crystal Palace thrives in a Windows Subsystem for Linux environment.

https://tradecraftgarden.org/wslsetup.html

## Usage

Use the `link` script to link a Windows DLL to a position-independent loader (and other resources) using steps provided in a specification file. Specification files are specific to each loader.

```bash
./link [loader.spec] [/path/to/dll] [out.bin]
```

For example:

```bash
./link /path/to/loader.spec demo/test.x64.dll out.x64.bin
```

To try out the same output (on Windows):

```bash
./demo/run.x64.exe out.x64.bin
```

To debug how Crystal Palace parses an object file, use `./coffparse`:

```bash
./coffparse [file.o]
```

## License

(c) 2025 Raphael Mudge, Adversary Fan Fiction Writers Guild
All rights reserved.

Crystal Palace is released under the 3-clause BSD license.

See [LICENSE](LICENSE) for more information.

## Credits

- [iced](https://github.com/icedland/iced) (C) 2018-present iced project and contributors (MIT License)
