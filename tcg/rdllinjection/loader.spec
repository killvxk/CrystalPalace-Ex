x86:
	load "bin/loader.x86.o"
		make pic

		fixptrs "_caller"

		push $DLL
			link "my_dll"

		export

x64:
	load "bin/loader.x64.o"
		make pic

		push $DLL
			link "my_dll"

		export
