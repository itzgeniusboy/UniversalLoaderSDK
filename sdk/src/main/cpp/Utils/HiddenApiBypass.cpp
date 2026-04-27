#include "HiddenApiBypass.h"
#include <android/log.h>

#define TAG "OneCore-ApiBypass"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace OneCore {
    /**
     * Bypasses Hidden API restrictions by modifying ART's internal policy.
     * Essential for Android 9+ support.
     */
    void bypassHiddenApi(JNIEnv* env) {
        LOGI("Initiating Hidden API Restriction Bypass...");
        
        jclass vm_runtime_class = env->FindClass("dalvik/system/VMRuntime");
        if (vm_runtime_class) {
            jmethodID get_runtime = env->GetStaticMethodID(vm_runtime_class, "getRuntime", "()Ldalvik/system/VMRuntime;");
            jobject runtime = env->CallStaticObjectMethod(vm_runtime_class, get_runtime);
            
            if (runtime) {
                // This call disables the restriction check for the current process
                jmethodID set_hidden_api_exemptions = env->GetMethodID(vm_runtime_class, "setHiddenApiExemptions", "([Ljava/lang/String;)V");
                
                jobjectArray empty_list = env->NewObjectArray(1, env->FindClass("java/lang/String"), env->NewStringUTF("L"));
                env->CallVoidMethod(runtime, set_hidden_api_exemptions, empty_list);
                
                LOGI("Hidden API Restriction: BYPASSED");
            }
        }
    }
}
