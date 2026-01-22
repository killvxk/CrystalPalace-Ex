/* Simple test DLL for Crystal Palace testing */
#include <windows.h>

__declspec(dllexport) int TestFunction(void) {
    return 42;
}

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    switch (fdwReason) {
        case DLL_PROCESS_ATTACH:
            break;
        case DLL_PROCESS_DETACH:
            break;
    }
    return TRUE;
}
