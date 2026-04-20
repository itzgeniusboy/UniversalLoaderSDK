#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/mman.h>
#include <vector>

#define TAG "NativeHook"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

/**
 * Basic memory protection helper
 */
bool set_memory_permissions(uintptr_t address, size_t size, int permissions) {
    uintptr_t page_start = address & ~(getpagesize() - 1);
    return mprotect((void*)page_start, size + (address - page_start), permissions) == 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_onecore_sdk_NativeHook_hookFunction(JNIEnv* env, jobject thiz, jlong target_addr, jlong replace_addr) {
    LOGD("Attempting native hook at 0x%lx to 0x%lx", (long)target_addr, (long)replace_addr);
    
    // In a real production scenario, use Dobby or Substrate here.
    // Example: DobbyHook((void*)target_addr, (void*)replace_addr, (void**)origin_addr);
    
    // Minimal implementation: PLT/GOT or simple branch patch pattern (ARM64 only demo)
#if defined(__aarch64__)
    // Simple 4-byte jump patch would go here if space allowed, 
    // but ARM64 requires 16 bytes for a full absolute jump.
    LOGD("ARM64 Inline hooking initialized (stub)");
#endif

    return 0; // Return original address if using Dobby
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_onecore_sdk_NativeHook_readMemoryNative(JNIEnv* env, jobject thiz, jlong addr, jint size) {
    jbyteArray result = env->NewByteArray(size);
    if (result == nullptr) return nullptr;

    void* source = (void*)addr;
    env->SetByteArrayRegion(result, 0, size, (const jbyte*)source);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_sdk_NativeHook_writeMemoryNative(JNIEnv* env, jobject thiz, jlong addr, jbyteArray data) {
    jsize size = env->GetArrayLength(data);
    jbyte* buffer = env->GetByteArrayElements(data, nullptr);
    
    if (set_memory_permissions(addr, size, PROT_READ | PROT_WRITE | PROT_EXEC)) {
        memcpy((void*)addr, buffer, size);
        env->ReleaseByteArrayElements(data, buffer, JNI_ABORT);
        return JNI_TRUE;
    }
    
    env->ReleaseByteArrayElements(data, buffer, JNI_ABORT);
    return JNI_FALSE;
}
