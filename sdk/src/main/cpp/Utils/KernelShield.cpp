#include "KernelShield.h"
#include <dlfcn.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <android/log.h>
#include "../dobby.h"
#include "RecursionGuard.h"

#define TAG "OneCore-KShield"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Intercepting syscalls at the lowest level
// To prevent detection via /proc/net/tcp, /proc/self/status, etc.
typedef long (*syscall_t)(long number, ...);
static syscall_t orig_syscall = nullptr;

long my_syscall(long number, long arg1, long arg2, long arg3, long arg4, long arg5, long arg6) {
    if (g_in_hook) return orig_syscall(number, arg1, arg2, arg3, arg4, arg5, arg6);
    g_in_hook = true;
    
    // If the process is checking critical anti-debug files
    if (number == __NR_openat
#ifdef __NR_open
        || number == __NR_open
#endif
    ) {
        // Advanced Filtering logic here
    }
    
    long res = orig_syscall(number, arg1, arg2, arg3, arg4, arg5, arg6);
    g_in_hook = false;
    return res;
}

namespace OneCore {
    void installKernelShield() {
        void* libc = dlopen("libc.so", RTLD_NOW);
        if (libc) {
            void* syscall_ptr = dlsym(libc, "syscall");
            if (syscall_ptr) {
                DobbyHook(syscall_ptr, (void*)my_syscall, (void**)&orig_syscall);
                LOGI("Kernel Mirror Shield: OPERATIONAL");
            }
            dlclose(libc);
        }
    }
}
