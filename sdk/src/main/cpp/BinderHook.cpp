#include "BinderHook.h"
#include <dlfcn.h>
#include <android/log.h>
#include "dobby.h"
#include "Utils/RecursionGuard.h"

#define TAG "OneCore-BinderHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

typedef int (*transact_t)(void* p1, uint32_t code, void* data, void* reply, uint32_t flags);
static transact_t orig_transact = nullptr;

// This intercepts Binder transactions between the game and the system
int my_transact(void* p1, uint32_t code, void* data, void* reply, uint32_t flags) {
    if (g_in_hook) return orig_transact(p1, code, data, reply, flags);
    g_in_hook = true;
    
    // Pass it through
    int res = orig_transact(p1, code, data, reply, flags);
    
    g_in_hook = false;
    return res;
}

namespace OneCore {
    void installBinderHooks() {
        void* libbinder = dlopen("libbinder.so", RTLD_NOW);
        if (libbinder) {
            // Mangled name for android::BpBinder::transact(unsigned int, android::Parcel const&, android::Parcel*, unsigned int)
            // This name varies slightly by Android version, but this is the common target.
            void* transact_ptr = dlsym(libbinder, "_ZN7android8BpBinder8transactEjRKNS_6ParcelEPS1_j");
            if (transact_ptr) {
                DobbyHook(transact_ptr, (void*)my_transact, (void**)&orig_transact);
                LOGI("Binder Transact Hook Installed successfully.");
            }
            dlclose(libbinder);
        }
    }
}
