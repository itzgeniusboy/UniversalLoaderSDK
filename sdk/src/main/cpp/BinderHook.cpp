#include "BinderHook.h"
#include <dlfcn.h>
#include <android/log.h>
#include "dobby.h"

#define TAG "OneCore-BinderHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

typedef int (*transact_t)(void* p1, uint32_t code, void* data, void* reply, uint32_t flags);
static transact_t orig_transact = nullptr;

// This intercepts Binder transactions between the game and the system
int my_transact(void* p1, uint32_t code, void* data, void* reply, uint32_t flags) {
    // In a full implementation, we would inspect 'data' and 'code' 
    // to redirect system service calls (like ActivityManager) to our virtual services.
    // For now, we pass it through but we have the "Master Gate" ready.
    return orig_transact(p1, code, data, reply, flags);
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
