/* Test file for stdcall entry symbol detection */

int __stdcall go(void) {
    int x = 10;
    int y = 20;
    return x + y;
}
