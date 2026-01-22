/* Test file for duplicate symbol names */

/* These static functions have the same name but different addresses */
static int helper(int x) {
    return x + 1;
}

int go(void) {
    return helper(10);
}

/* Another static with same name in different scope - creates duplicate symbol */
static int helper(int x);  /* forward declaration to force separate symbol entry */
