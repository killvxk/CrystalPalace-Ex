# Test spec file for stdcall entry with +gofirst and +optimize
x86:
load "test_stdcall_entry.x86.o"
make pic +gofirst +optimize
export
