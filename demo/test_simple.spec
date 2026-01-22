# Test spec file for simple PIC output
x86:
load "test_simple.x86.o"
make pic
export

x64:
load "test_simple.x64.o"
make pic64
export
