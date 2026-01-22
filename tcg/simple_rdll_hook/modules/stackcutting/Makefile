CC=i686-w64-mingw32-gcc
CC_64=x86_64-w64-mingw32-gcc
CFLAGS=-O1 -fno-jump-tables -shared -Wall -Wno-pointer-arith

.PHONY: all x86 x64 clean

all: x86 x64

bin:
	mkdir -p bin

# our proxy.c expects a very specific stackframe layout and function prologue. We disable optimizations to
# preserve it. This applies to x86 and x64.

x86: bin
	$(CC) -DWIN_X86 -O0 -fno-jump-tables -shared -Wall -Wno-pointer-arith -c src/proxy.c -o bin/proxy.x86.o
	$(CC) -DWIN_X86 $(CFLAGS) -c src/stackcut.c -o bin/stackcut.x86.o
	$(CC) -DWIN_X86 $(CFLAGS) -c src/stackcut_setup.c -o bin/stackcut_setup.x86.o

x64: bin
	$(CC_64) -DWIN_X64 -O0 -fno-jump-tables -shared -Wall -Wno-pointer-arith -c src/proxy.c -o bin/proxy.x64.o
	$(CC_64) -DWIN_X64 $(CFLAGS) -c src/stackcut.c -o bin/stackcut.x64.o
	$(CC_64) -DWIN_X64 $(CFLAGS) -c src/stackcut_setup.c -o bin/stackcut_setup.x64.o

clean:
	rm -rf bin
