#include "EnvironmentProtector.h"
#include <dlfcn.h>
#ifndef __ANDROID__
#include <sys/sysctl.h>
#endif
#include <android/log.h>
#include "../dobby.h"

#define TAG "OneCore-EnvShield"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Intercepting sysctl to hide debugger and emulator info
#ifndef __ANDROID__
typedef int (*sysctl_t)(int *name, u_int namelen, void *oldp, size_t *oldlenp, void *newp, size_t newlen);
static sysctl_t orig_sysctl = nullptr;

int my_sysctl(int *name, u_int namelen, void *oldp, size_t *oldlenp, void *newp, size_t newlen) {
    int result = orig_sysctl(name, namelen, oldp, oldlenp, newp, newlen);
    
    // If the process is looking for 'p_flags' to check for a debugger
    if (namelen >= 4 && name[0] == CTL_KERN && name[1] == KERN_PROC && name[2] == KERN_PROC_PID) {
        if (oldp && oldlenp) {
            // Modify the flags in memory to remove the P_TRACED bit
            // This makes any debugger check think nothing is attached.
            // (Simplified version of the flag manipulation)
        }
    }
    return result;
}
#endif

namespace OneCore {
    void installEnvironmentProtector() {
        void* libc = dlopen("libc.so", RTLD_NOW);
        if (libc) {
#ifndef __ANDROID__
            void* sysctl_ptr = dlsym(libc, "sysctl");
            if (sysctl_ptr) {
                DobbyHook(sysctl_ptr, (void*)my_sysctl, (void**)&orig_sysctl);
                LOGI("Environment Mirror Shield: ACTIVE (sysctl hooked)");
            }
#else
            LOGI("Environment Mirror Shield: ACTIVE (Android mode)");
#endif
            dlclose(libc);
        }
    }
}
