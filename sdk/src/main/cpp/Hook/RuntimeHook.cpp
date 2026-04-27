#include "RuntimeHook.h"
#include <dlfcn.h>
#include <unistd.h>
#include <sys/types.h>
#include <android/log.h>
#include "../dobby.h"

#define TAG "OneCore-RuntimeHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

typedef pid_t (*fork_t)();
static fork_t orig_fork = nullptr;

pid_t my_fork() {
    // In many virtual environments, we block fork to prevent detection
    // or we handle it to keep the child inside the virtual container.
    LOGI("Process tried to fork! Blocking for security.");
    return -1; 
}

namespace OneCore {
    void installRuntimeHooks() {
        void* libc = dlopen("libc.so", RTLD_NOW);
        if (libc) {
            void* fork_ptr = dlsym(libc, "fork");
            if (fork_ptr) DobbyHook(fork_ptr, (void*)my_fork, (void**)&orig_fork);
            
            LOGI("Runtime Hooks (Anti-Fork) Installed.");
            dlclose(libc);
        }
    }
}
