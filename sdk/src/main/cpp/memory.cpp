#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/uio.h>
#include <android/log.h>

#define TAG "NativeMemory"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * Accesses memory via process_vm_readv/writev (more robust for inter-process)
 * or fallback to direct access for same-process.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_sdk_NativeHook_readProcessMemory(JNIEnv* env, jobject thiz, jint pid, jlong addr, jbyteArray buffer) {
    jsize size = env->GetArrayLength(buffer);
    jbyte* local_buf = env->GetByteArrayElements(buffer, nullptr);

    struct iovec local[1];
    struct iovec remote[1];

    local[0].iov_base = local_buf;
    local[0].iov_len = size;
    remote[0].iov_base = (void*)addr;
    remote[0].iov_len = size;

    ssize_t nread = process_vm_readv(pid, local, 1, remote, 1, 0);
    
    env->ReleaseByteArrayElements(buffer, local_buf, 0);

    return (nread == size) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_sdk_NativeHook_writeProcessMemory(JNIEnv* env, jobject thiz, jint pid, jlong addr, jbyteArray buffer) {
    jsize size = env->GetArrayLength(buffer);
    jbyte* local_buf = env->GetByteArrayElements(buffer, nullptr);

    struct iovec local[1];
    struct iovec remote[1];

    local[0].iov_base = local_buf;
    local[0].iov_len = size;
    remote[0].iov_base = (void*)addr;
    remote[0].iov_len = size;

    ssize_t nwritten = process_vm_writev(pid, local, 1, remote, 1, 0);

    env->ReleaseByteArrayElements(buffer, local_buf, JNI_ABORT);

    return (nwritten == size) ? JNI_TRUE : JNI_FALSE;
}
