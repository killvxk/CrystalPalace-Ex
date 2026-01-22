/* Test file for stdcall decorated symbols */

/* Import with stdcall decoration */
extern __declspec(dllimport) int __stdcall KERNEL32$GetLastError(void);
extern __declspec(dllimport) void* __stdcall KERNEL32$GetProcAddress(void* hModule, const char* lpProcName);

int __stdcall go(void) {
    int err = KERNEL32$GetLastError();
    void* proc = KERNEL32$GetProcAddress(0, "test");
    return err;
}
