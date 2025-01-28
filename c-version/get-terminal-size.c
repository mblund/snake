#include <stdio.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <string.h>

int main() {
    struct winsize ws;

    // Check if STDOUT_FILENO is a terminal
    if (isatty(STDOUT_FILENO) == 0) {
        printf("STDOUT_FILENO is not a terminal.\n");
        return -1;
    }

    printf("TIOCGWINSZ: %lx\n", (unsigned long)TIOCGWINSZ);    

    // Make the ioctl call to get terminal size
    int result = ioctl(STDOUT_FILENO, TIOCGWINSZ, &ws);
    if (result == -1) {
        printf("ioctl error: %s\n", strerror(errno));
        return -2;
    }

    // Print rows and columns
    printf("Rows: %d, Columns: %d\n", ws.ws_row, ws.ws_col);
    return 0;
}
