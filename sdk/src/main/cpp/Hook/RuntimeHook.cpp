#include "RuntimeHook.h"
#include <dlfcn.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/system_properties.h>
#include <signal.h>
#include <errno.h>
#include <string.h>
#include <stdarg.h>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <stdio.h>
#include <android/log.h>
#include "../dobby.h"
#include "../Utils/RecursionGuard.h"

#define TAG "OneCore-RuntimeHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef pid_t (*fork_t)();
static fork_t orig_fork = nullptr;

typedef void* (*dlopen_t)(const char* filename, int flag);
static dlopen_t orig_dlopen = nullptr;

typedef int (*__system_property_get_t)(const char* name, char* value);
static __system_property_get_t orig_system_property_get = nullptr;

typedef int (*open_t)(const char* pathname, int flags, ...);
static open_t orig_open = nullptr;

typedef uid_t (*getuid_t)();
static getuid_t orig_getuid = nullptr;
static getuid_t orig_geteuid = nullptr;

typedef FILE* (*fopen_t)(const char* filename, const char* mode);
static fopen_t orig_fopen = nullptr;

typedef int (*access_t)(const char* pathname, int mode);
static access_t orig_access = nullptr;

typedef int (*stat_t)(const char* pathname, struct stat* buf);
static stat_t orig_stat = nullptr;

typedef long (*ptrace_t)(int request, pid_t pid, void* addr, void* data);
static ptrace_t orig_ptrace = nullptr;

pid_t rt_my_fork() {
    if (g_in_hook) return orig_fork();
    g_in_hook = true;
    LOGI("Process tried to fork! Blocking for security.");
    g_in_hook = false;
    return -1; 
}

void* rt_my_dlopen(const char* filename, int flag) {
    if (g_in_hook) return orig_dlopen(filename, flag);
    g_in_hook = true;
    
    if (filename) {
        LOGD("[RuntimeHook] dlopen: %s", filename);
        if (strstr(filename, "frida") || strstr(filename, "xposed")) {
            LOGW("[RuntimeHook] Block dlopen: %s", filename);
            g_in_hook = false;
            return nullptr;
        }
    }
    
    void* handle = orig_dlopen(filename, flag);
    
    if (!handle && filename) {
        LOGE("[RuntimeHook] dlopen FAILED for: %s", filename);
    }
    
    g_in_hook = false;
    return handle;
}

int rt_my_system_property_get(const char* name, char* value) {
    if (g_in_hook) return orig_system_property_get(name, value);
    g_in_hook = true;
    
    int result = orig_system_property_get(name, value);
    if (name) {
        if (strcmp(name, "ro.debuggable") == 0) {
            strcpy(value, "0");
        } else if (strcmp(name, "ro.secure") == 0) {
            strcpy(value, "1");
        } else if (strcmp(name, "ro.build.tags") == 0) {
            strcpy(value, "release-keys");
        }
        LOGD("[RuntimeHook] property_get: %s = %s", name, value ? value : "NULL");
    }
    
    g_in_hook = false;
    return result;
}

const char* suspicious[] = {
    "/system/xbin/su",
    "/sbin/su",
    "/system/bin/su",
    "magisk",
    "xposed"
};

int rt_my_open(const char* pathname, int flags, ...) {
    if (g_in_hook) {
        va_list args;
        va_start(args, flags);
        mode_t mode = va_arg(args, mode_t);
        va_end(args);
        return orig_open(pathname, flags, mode);
    }
    g_in_hook = true;

    if (pathname) {
        for (int i = 0; i < sizeof(suspicious)/sizeof(suspicious[0]); i++) {
            if (strstr(pathname, suspicious[i])) {
                LOGW("[RuntimeHook] Blocked access: %s", pathname);
                g_in_hook = false;
                errno = ENOENT;
                return -1;
            }
        }
    }

    va_list args;
    va_start(args, flags);
    mode_t mode = va_arg(args, mode_t);
    va_end(args);
    
    int result = orig_open(pathname, flags, mode);
    g_in_hook = false;
    return result;
}

uid_t rt_my_getuid() {
    return 10000; // normal app UID
}

uid_t rt_my_geteuid() {
    return 10000;
}

FILE* rt_my_fopen(const char* path, const char* mode) {
    if (g_in_hook) return orig_fopen(path, mode);
    g_in_hook = true;

    if (path && (strstr(path, "/proc/") || strstr(path, "maps") || strstr(path, "status"))) {
        LOGW("[RuntimeHook] fopen accessed internal path: %s - ALLOWING", path);
    }

    FILE* result = orig_fopen(path, mode);
    g_in_hook = false;
    return result;
}

int rt_my_access(const char* path, int mode) {
    if (g_in_hook) return orig_access(path, mode);
    g_in_hook = true;

    if (path && (strstr(path, "/su") || strstr(path, "/bin/su") || strstr(path, "magisk") || strstr(path, "xposed"))) {
        LOGW("[RuntimeHook] Block access to sensitive path: %s", path);
        g_in_hook = false;
        errno = ENOENT;
        return -1;
    }

    int result = orig_access(path, mode);
    g_in_hook = false;
    return result;
}

int rt_my_stat(const char* path, struct stat* buf) {
    if (g_in_hook) return orig_stat(path, buf);
    g_in_hook = true;

    if (path && (strstr(path, "su") || strstr(path, "magisk"))) {
        LOGW("[RuntimeHook] stat accessed suspicious path: %s - ALLOWING", path);
    }

    int result = orig_stat(path, buf);
    g_in_hook = false;
    return result;
}

long rt_my_ptrace(int request, pid_t pid, void* addr, void* data) {
    LOGW("[RuntimeHook] ptrace called (request=%d, pid=%d) - ALLOWING", request, pid);
    return orig_ptrace(request, pid, addr, data);
}

void crash_handler(int sig, siginfo_t* info, void* context) {
    LOGE("CRASH DETECTED: Signal %d at address %p", sig, info->si_addr);
    
    // Re-raise signal to ensure default handler (and tombstone) runs
    signal(sig, SIG_DFL);
    raise(sig);
}

namespace OneCore {
    void installRuntimeHooks() {
        LOGI("Installing Runtime Hooks (dlopen, signals, props, uid, open)...");

        // Crash Detection
        struct sigaction sa;
        sa.sa_flags = SA_SIGINFO;
        sa.sa_sigaction = crash_handler;
        sigemptyset(&sa.sa_mask);
        sigaction(SIGSEGV, &sa, NULL);
        sigaction(SIGABRT, &sa, NULL);
        sigaction(SIGBUS, &sa, NULL);
        sigaction(SIGILL, &sa, NULL);

        void* libc = dlopen("libc.so", RTLD_NOW);
        if (libc) {
            void* fork_ptr = dlsym(libc, "fork");
            if (fork_ptr) DobbyHook(fork_ptr, (void*)rt_my_fork, (void**)&orig_fork);
            
            void* sys_prop_ptr = dlsym(libc, "__system_property_get");
            if (sys_prop_ptr) DobbyHook(sys_prop_ptr, (void*)rt_my_system_property_get, (void**)&orig_system_property_get);

            void* getuid_ptr = dlsym(libc, "getuid");
            if (getuid_ptr) DobbyHook(getuid_ptr, (void*)rt_my_getuid, (void**)&orig_getuid);

            void* geteuid_ptr = dlsym(libc, "geteuid");
            if (geteuid_ptr) DobbyHook(geteuid_ptr, (void*)rt_my_geteuid, (void**)&orig_geteuid);

            void* open_ptr = dlsym(libc, "open");
            if (open_ptr) DobbyHook(open_ptr, (void*)rt_my_open, (void**)&orig_open);

            void* fopen_ptr = dlsym(libc, "fopen");
            if (fopen_ptr) DobbyHook(fopen_ptr, (void*)rt_my_fopen, (void**)&orig_fopen);

            void* access_ptr = dlsym(libc, "access");
            if (access_ptr) DobbyHook(access_ptr, (void*)rt_my_access, (void**)&orig_access);

            void* stat_ptr = dlsym(libc, "stat");
            if (stat_ptr) DobbyHook(stat_ptr, (void*)rt_my_stat, (void**)&orig_stat);

            void* ptrace_ptr = dlsym(libc, "ptrace");
            if (ptrace_ptr) DobbyHook(ptrace_ptr, (void*)rt_my_ptrace, (void**)&orig_ptrace);

            LOGI("Libc Hooks Installed.");
            dlclose(libc);
        }

        void* libdl = dlopen("libdl.so", RTLD_NOW);
        if (libdl) {
            void* dlopen_ptr = dlsym(libdl, "dlopen");
            if (!dlopen_ptr) {
                void* libc_for_dl = dlopen("libc.so", RTLD_NOW);
                dlopen_ptr = dlsym(libc_for_dl, "dlopen");
                dlclose(libc_for_dl);
            }

            if (dlopen_ptr) DobbyHook(dlopen_ptr, (void*)rt_my_dlopen, (void**)&orig_dlopen);
            
            LOGI("Libdl Hooks Installed.");
            dlclose(libdl);
        }
    }
}
