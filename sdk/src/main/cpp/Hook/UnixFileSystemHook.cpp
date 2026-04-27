#include "UnixFileSystemHook.h"
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <android/log.h>
#include "../dobby.h"

#define TAG "OneCore-FSHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Function pointers for original system calls
typedef int (*open_t)(const char *pathname, int flags, ...);
static open_t orig_open = nullptr;

typedef int (*access_t)(const char *pathname, int mode);
static access_t orig_access = nullptr;

// Redirection logic
const char* redirect_path(const char* path) {
    // This is where we would check if the path needs to be redirected to our virtual root
    // For example: /data/data/com.game -> /data/data/com.onecore/virtual/
    if (path && strstr(path, "/proc/self/cmdline")) {
        // Example: hide actual package name
    }
    return path;
}

int my_open(const char *pathname, int flags, mode_t mode) {
    const char* redirected = redirect_path(pathname);
    return orig_open(redirected, flags, mode);
}

int my_access(const char *pathname, int mode) {
    const char* redirected = redirect_path(pathname);
    return orig_access(redirected, mode);
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
