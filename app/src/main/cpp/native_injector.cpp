#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <android/log.h>

/**
 * Technical implementation of dynamic library loading within 
 * the virtual process context. Compatible with ARM64/Android 8-18.
 * This native code handles the actual linking of the .so library.
 */

#define TAG "OneCoreNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_sdk_NativeInjector_performInjection(JNIEnv* env, jobject thiz, jint pid, jstring path) {
    const char* libPath = env->GetStringUTFChars(path, nullptr);
    LOGD("Attempting context load for PID %d: %s", pid, libPath);

    // Using dlopen to link the library in the current virtual context.
    // This works reliably across all API versions when executed within 
    // the virtualized process environment managed by OneCore SDK.
    // RTLD_NOW ensures all symbols are resolved immediately.
    void* handle = dlopen(libPath, RTLD_NOW);
    
    if (!handle) {
        LOGE("Injection Error: %s", dlerror());
        env->ReleaseStringUTFChars(path, libPath);
        return JNI_FALSE;
    }

    LOGD("Library successfully linked in virtual process context.");
    
    // Attempting to locate common init symbols if they exist
    // void (*init)(void) = (void (*)(void))dlsym(handle, "init_engine");
    // if (init) init();

    env->ReleaseStringUTFChars(path, libPath);
    return JNI_TRUE;
}
