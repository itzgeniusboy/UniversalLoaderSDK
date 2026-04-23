#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include "dobby.h"

#define TAG "OneCore-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::string g_virtual_root;
static std::string g_package_name;

// Syscall pointers for redirection
static int (*orig_open)(const char *pathname, int flags, mode_t mode) = nullptr;
static int (*orig_openat)(int dirfd, const char *pathname, int flags, mode_t mode) = nullptr;
static int (*orig_access)(const char *pathname, int mode) = nullptr;
static int (*orig_faccessat)(int dirfd, const char *pathname, int mode, int flags) = nullptr;
static int (*orig_stat)(const char *pathname, struct stat *buf) = nullptr;
static int (*orig_lstat)(const char *pathname, struct stat *buf) = nullptr;
static int (*orig_fstatat)(int dirfd, const char *pathname, struct stat *buf, int flags) = nullptr;
static ssize_t (*orig_readlink)(const char *pathname, char *buf, size_t bufsiz) = nullptr;
static void* (*orig_opendir)(const char *name) = nullptr;
static void* (*orig_opendir2)(const char *name, int flags) = nullptr;

/**
 * Path Redirection Engine for BGMI Sandbox.
 * Maps original paths to the virtual session root.
 */
static std::string redirect_path(const char* path) {
    if (!path || g_virtual_root.empty()) return path ? path : "";
    
    std::string s_path(path);
    
    // Normalize path: handle double slashes and trailing slashes
    while (s_path.find("//") != std::string::npos) {
        s_path.replace(s_path.find("//"), 2, "/");
    }
    if (s_path.length() > 1 && s_path.back() == '/') {
        s_path.pop_back();
    }
    
    // Anti-Detection: Hide common root files
    if (s_path.find("/su") != std::string::npos || 
        s_path.find("/magisk") != std::string::npos ||
        s_path.find("busybox") != std::string::npos) {
        return "/system/bin/non_existent_file_onecore";
    }

    // 1. Data Dir Redirection (/data/data/...)
    std::string data_target = "/data/data/" + g_package_name;
    if (s_path.compare(0, data_target.length(), data_target) == 0) {
        return g_virtual_root + "/data" + s_path.substr(data_target.length());
    }
    
    // 2. OBB Dir Redirection (/storage/emulated/0/Android/obb/...)
    const char* obb_roots[] = {
        "/storage/emulated/0/Android/obb/",
        "/sdcard/Android/obb/",
        "/mnt/shell/emulated/0/Android/obb/",
        "/storage/self/primary/Android/obb/",
        "/mnt/media_rw/sdcard0/Android/obb/"
    };
    
    for (const char* root : obb_roots) {
        std::string target = std::string(root) + g_package_name;
        if (s_path.compare(0, target.length(), target) == 0) {
            std::string redirected = g_virtual_root + "/obb" + s_path.substr(target.length());
            return redirected;
        }
    }

    // 3. External Data Redirection (/storage/emulated/0/Android/data/...)
    const char* ext_data_roots[] = {
        "/storage/emulated/0/Android/data/",
        "/sdcard/Android/data/",
        "/storage/self/primary/Android/data/"
    };

    for (const char* root : ext_data_roots) {
        std::string target = std::string(root) + g_package_name;
        if (s_path.compare(0, target.length(), target) == 0) {
            return g_virtual_root + "/data" + s_path.substr(target.length());
        }
    }

    return s_path;
}

int my_open(const char *pathname, int flags, mode_t mode) {
    return orig_open(redirect_path(pathname).c_str(), flags, mode);
}

int my_openat(int dirfd, const char *pathname, int flags, mode_t mode) {
    if (pathname && pathname[0] == '/') {
        return orig_openat(dirfd, redirect_path(pathname).c_str(), flags, mode);
    }
    return orig_openat(dirfd, pathname, flags, mode);
}

int my_access(const char *pathname, int mode) {
    return orig_access(redirect_path(pathname).c_str(), mode);
}

int my_faccessat(int dirfd, const char *pathname, int mode, int flags) {
    if (pathname && pathname[0] == '/') {
        return orig_faccessat(dirfd, redirect_path(pathname).c_str(), mode, flags);
    }
    return orig_faccessat(dirfd, pathname, mode, flags);
}

int my_stat(const char *pathname, struct stat *buf) {
    return orig_stat(redirect_path(pathname).c_str(), buf);
}

int my_lstat(const char *pathname, struct stat *buf) {
    return orig_lstat(redirect_path(pathname).c_str(), buf);
}

int my_fstatat(int dirfd, const char *pathname, struct stat *buf, int flags) {
    if (pathname && pathname[0] == '/') {
        return orig_fstatat(dirfd, redirect_path(pathname).c_str(), buf, flags);
    }
    return orig_fstatat(dirfd, pathname, buf, flags);
}

ssize_t my_readlink(const char *pathname, char *buf, size_t bufsiz) {
    return orig_readlink(redirect_path(pathname).c_str(), buf, bufsiz);
}

void* my_opendir(const char *name) {
    return orig_opendir(redirect_path(name).c_str());
}

void* my_opendir2(const char *name, int flags) {
    return orig_opendir2(redirect_path(name).c_str(), flags);
}

// --- JNI Implementation for NativeHookManager ---

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_NativeHookManager_initHooks(JNIEnv* env, jclass clazz, jstring virtual_root, jstring package_name) {
    const char* v_root = env->GetStringUTFChars(virtual_root, nullptr);
    const char* p_name = env->GetStringUTFChars(package_name, nullptr);
    g_virtual_root = v_root;
    g_package_name = p_name;

    LOGI("Initializing Sandbox IO Layer for: %s", p_name);

    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        DobbyHook(dlsym(libc, "open"), (void*)my_open, (void**)&orig_open);
        DobbyHook(dlsym(libc, "openat"), (void*)my_openat, (void**)&orig_openat);
        DobbyHook(dlsym(libc, "access"), (void*)my_access, (void**)&orig_access);
        DobbyHook(dlsym(libc, "faccessat"), (void*)my_faccessat, (void**)&orig_faccessat);
        DobbyHook(dlsym(libc, "stat"), (void*)my_stat, (void**)&orig_stat);
        DobbyHook(dlsym(libc, "lstat"), (void*)my_lstat, (void**)&orig_lstat);
        DobbyHook(dlsym(libc, "fstatat"), (void*)my_fstatat, (void**)&orig_fstatat);
        DobbyHook(dlsym(libc, "readlink"), (void*)my_readlink, (void**)&orig_readlink);
        DobbyHook(dlsym(libc, "opendir"), (void*)my_opendir, (void**)&orig_opendir);
        
        void* opendir2_ptr = dlsym(libc, "__opendir2");
        if (opendir2_ptr) DobbyHook(opendir2_ptr, (void*)my_opendir2, (void**)&orig_opendir2);
        
        LOGI("Extended Native Hooks (IO + DIR) INSTALLED.");
        dlclose(libc);
    }

    env->ReleaseStringUTFChars(virtual_root, v_root);
    env->ReleaseStringUTFChars(package_name, p_name);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_IORedirector_initNativeHooks(JNIEnv* env, jclass clazz, jstring virtual_root, jstring package_name) {
    Java_com_onecore_sdk_NativeHookManager_initHooks(env, clazz, virtual_root, package_name);
}

// --- JNI Implementation for NativeHook (Memory Ops) ---

extern "C" JNIEXPORT jlong JNICALL
Java_com_onecore_sdk_NativeHook_hookFunction(JNIEnv* env, jobject obj, jlong target, jlong replace) {
    void* origin = nullptr;
    if (DobbyHook((void*)target, (void*)replace, &origin) == 0) {
        return (jlong)origin;
    }
    return 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_onecore_sdk_NativeHook_readMemoryNative(JNIEnv* env, jobject obj, jlong addr, jint size) {
    if (addr == 0 || size <= 0) return nullptr;
    jbyteArray result = env->NewByteArray(size);
    jbyte* buffer = (jbyte*)addr;
    env->SetByteArrayRegion(result, 0, size, buffer);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_sdk_NativeHook_writeMemoryNative(JNIEnv* env, jobject obj, jlong addr, jbyteArray data) {
    if (addr == 0 || data == nullptr) return JNI_FALSE;
    jsize size = env->GetArrayLength(data);
    jbyte* buffer = env->GetByteArrayElements(data, nullptr);
    
    // Use mprotect to ensure memory is writable (typical in Unreal Engine hacks)
    long pagesize = sysconf(_SC_PAGESIZE);
    void* page = (void*)(addr & ~(pagesize - 1));
    mprotect(page, pagesize * 2, PROT_READ | PROT_WRITE | PROT_EXEC);
    
    memcpy((void*)addr, buffer, size);
    env->ReleaseByteArrayElements(data, buffer, JNI_ABORT);
    return JNI_TRUE;
}

// --- UID Spoofing Hook ---
static uid_t (*orig_getuid)() = nullptr;
uid_t my_getuid() {
    return 10100; // Simulated sandbox UID
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_core_UidSpoofing_applyNative(JNIEnv* env, jclass clazz, jint fakeUid) {
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        DobbyHook(dlsym(libc, "getuid"), (void*)my_getuid, (void**)&orig_getuid);
        DobbyHook(dlsym(libc, "getcallinguid"), (void*)my_getuid, nullptr);
        dlclose(libc);
    }
}
