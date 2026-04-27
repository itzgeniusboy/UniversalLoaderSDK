#include "UnixFileSystemHook.h"
#include <string.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <android/log.h>
#include "../dobby.h"
#include "../Utils/RecursionGuard.h"

#define TAG "OneCore-FSHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// Function pointers for original system calls
typedef int (*open_t)(const char *pathname, int flags, mode_t mode);
static open_t orig_open = nullptr;

typedef int (*access_t)(const char *pathname, int mode);
static access_t orig_access = nullptr;

// Redirection logic
const char* redirect_path(const char* path) {
    if (!path) return path;

    // Log suspicious access patterns
    if (strstr(path, "/proc/") || strstr(path, "/sys/") || strstr(path, "su") || strstr(path, "magisk")) {
        LOGD("[FSHook] Suspicious access: %s", path);
    }

    // This is where we would check if the path needs to be redirected to our virtual root
    // For example: /data/data/com.game -> /data/data/com.onecore/virtual/
    if (strstr(path, "/proc/self/cmdline")) {
        // Example: hide actual package name
    }
    return path;
}

static int my_open(const char *pathname, int flags, mode_t mode) {
    if (g_in_hook) return orig_open(pathname, flags, mode);
    g_in_hook = true;

    const char* redirected = redirect_path(pathname);
    int result = orig_open(redirected, flags, mode);

    g_in_hook = false;
    return result;
}

static int my_access(const char *pathname, int mode) {
    if (g_in_hook) return orig_access(pathname, mode);
    g_in_hook = true;

    const char* redirected = redirect_path(pathname);
    int result = orig_access(redirected, mode);

    g_in_hook = false;
    return result;
}

namespace OneCore {
    void installFileSystemHooks() {
        void* libc = dlopen("libc.so", RTLD_NOW);
        if (libc) {
            void* open_ptr = dlsym(libc, "open");
            void* access_ptr = dlsym(libc, "access");
            
            if (open_ptr) DobbyHook(open_ptr, (void*)my_open, (void**)&orig_open);
            if (access_ptr) DobbyHook(access_ptr, (void*)my_access, (void**)&orig_access);
            
            LOGI("File System Hooks Installed.");
            dlclose(libc);
        }
    }
}
