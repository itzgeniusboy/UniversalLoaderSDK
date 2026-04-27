#include "Stealth.h"
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>
#include <string.h>
#include <stdio.h>
#include <vector>

#define TAG "OneCore-Stealth"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace OneCore {
    // This function attempts to "anonymousize" the memory mapping of our library
    // to prevent simple string-based detection in /proc/self/maps
    void enableStealthMode() {
        FILE* f = fopen("/proc/self/maps", "r");
        if (!f) return;

        char line[512];
        while (fgets(line, sizeof(line), f)) {
            if (strstr(line, "libonecore_native.so") || strstr(line, "libdobby.so")) {
                uintptr_t start, end;
                char perms[5];
                if (sscanf(line, "%lx-%lx %s", &start, &end, perms) == 3) {
                    // Note: We don't actually unmap it (that would crash us),
                    // but we can modify the mapping name in some kernels or 
                    // use advanced techniques to shadow it.
                    // For now, we log the protection and prepare for shadowing.
                    LOGI("Protecting segment: %lx-%lx", start, end);
                }
            }
        }
        fclose(f);
    }
}
