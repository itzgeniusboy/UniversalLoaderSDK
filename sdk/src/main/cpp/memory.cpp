#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/uio.h>
#include <android/log.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>

#define TAG "NativeMemory"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#if __ANDROID_API__ < 23
static ssize_t process_vm_readv_fallback(pid_t pid, const struct iovec *local_iov, unsigned long liovcnt, const struct iovec *remote_iov, unsigned long riovcnt, unsigned long flags) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", pid);
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        LOGE("Failed to open %s: %s", path, strerror(errno));
        return -1;
    }

    ssize_t total_read = 0;
    for (unsigned long i = 0; i < riovcnt; i++) {
        ssize_t n = pread64(fd, local_iov[i].iov_base, local_iov[i].iov_len, (off64_t)remote_iov[i].iov_base);
        if (n != (ssize_t)local_iov[i].iov_len) {
            LOGE("Failed to read at %p: %s", remote_iov[i].iov_base, strerror(errno));
            close(fd);
            return -1;
        }
        total_read += n;
    }

    close(fd);
    return total_read;
}

static ssize_t process_vm_writev_fallback(pid_t pid, const struct iovec *local_iov, unsigned long liovcnt, const struct iovec *remote_iov, unsigned long riovcnt, unsigned long flags) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", pid);
    int fd = open(path, O_WRONLY);
    if (fd < 0) {
        LOGE("Failed to open %s: %s", path, strerror(errno));
        return -1;
    }

    ssize_t total_written = 0;
    for (unsigned long i = 0; i < riovcnt; i++) {
        ssize_t n = pwrite64(fd, local_iov[i].iov_base, local_iov[i].iov_len, (off64_t)remote_iov[i].iov_base);
        if (n != (ssize_t)local_iov[i].iov_len) {
            LOGE("Failed to write at %p: %s", remote_iov[i].iov_base, strerror(errno));
            close(fd);
            return -1;
        }
        total_written += n;
    }

    close(fd);
    return total_written;
}
#endif

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_sdk_NativeHook_readProcessMemory(JNIEnv* env, jobject thiz, jint pid, jlong addr, jbyteArray buffer) {
    if (buffer == nullptr) {
        LOGE("readProcessMemory: buffer is null");
        return JNI_FALSE;
    }
    jsize size = env->GetArrayLength(buffer);
    jbyte* local_buf = env->GetByteArrayElements(buffer, nullptr);
    if (local_buf == nullptr) {
        LOGE("readProcessMemory: failed to get byte array elements");
        return JNI_FALSE;
    }

    struct iovec local[1];
    struct iovec remote[1];

    local[0].iov_base = local_buf;
    local[0].iov_len = size;
    remote[0].iov_base = (void*)addr;
    remote[0].iov_len = size;

    ssize_t nread;
#if __ANDROID_API__ >= 23
    nread = process_vm_readv(pid, local, 1, remote, 1, 0);
#else
    nread = process_vm_readv_fallback(pid, local, 1, remote, 1, 0);
#endif
    
    if (nread != size) {
        LOGE("readProcessMemory failed: pid=%d, addr=%p, requested=%d, read=%zd, errno=%d (%s)", 
             pid, (void*)addr, size, nread, errno, strerror(errno));
    }

    env->ReleaseByteArrayElements(buffer, local_buf, 0);
    return (nread == size) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_sdk_NativeHook_writeProcessMemory(JNIEnv* env, jobject thiz, jint pid, jlong addr, jbyteArray buffer) {
    if (buffer == nullptr) {
        LOGE("writeProcessMemory: buffer is null");
        return JNI_FALSE;
    }
    jsize size = env->GetArrayLength(buffer);
    jbyte* local_buf = env->GetByteArrayElements(buffer, nullptr);
    if (local_buf == nullptr) {
        LOGE("writeProcessMemory: failed to get byte array elements");
        return JNI_FALSE;
    }

    struct iovec local[1];
    struct iovec remote[1];

    local[0].iov_base = local_buf;
    local[0].iov_len = size;
    remote[0].iov_base = (void*)addr;
    remote[0].iov_len = size;

    ssize_t nwritten;
#if __ANDROID_API__ >= 23
    nwritten = process_vm_writev(pid, local, 1, remote, 1, 0);
#else
    nwritten = process_vm_writev_fallback(pid, local, 1, remote, 1, 0);
#endif

    if (nwritten != size) {
        LOGE("writeProcessMemory failed: pid=%d, addr=%p, requested=%d, written=%zd, errno=%d (%s)", 
             pid, (void*)addr, size, nwritten, errno, strerror(errno));
    }

    env->ReleaseByteArrayElements(buffer, local_buf, JNI_ABORT);
    return (nwritten == size) ? JNI_TRUE : JNI_FALSE;
}
