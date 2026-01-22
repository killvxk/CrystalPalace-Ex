CC=i686-w64-mingw32-gcc
CC_64=x86_64-w64-mingw32-gcc
CFLAGS=-O1 -fno-jump-tables -shared -Wall -Wno-pointer-arith

.PHONY: all x86 x64 clean

all: x86 x64

bin:
	mkdir -p bin

x86: bin
	$(CC) -DWIN_X86 $(CFLAGS) -c src/loader.c -o bin/loader.x86.o
	$(CC) -DWIN_X86 $(CFLAGS) -c src/free.c -o bin/free.x86.o
	$(CC) -DWIN_X86 $(CFLAGS) -c src/hook.c -o bin/hook.x86.o

x64: bin
	$(CC_64) -DWIN_X64 $(CFLAGS) -c src/loader.c -o bin/loader.x64.o
	$(CC_64) -DWIN_X64 $(CFLAGS) -c src/free.c -o bin/free.x64.o
	$(CC_64) -DWIN_X64 $(CFLAGS) -c src/hook.c -o bin/hook.x64.o

clean:
	rm -rf bin
