#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include "dobby.h"

#define TAG "OneCore-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::string g_virtual_root;
static std::string g_package_name;
static std::string g_host_pkg;

// Original function pointers
static int (*orig_open)(const char *pathname, int flags, mode_t mode) = nullptr;
static int (*orig_openat)(int dirfd, const char *pathname, int flags, mode_t mode) = nullptr;
static int (*orig_access)(const char *pathname, int mode) = nullptr;
static int (*orig_stat)(const char *pathname, struct stat *buf) = nullptr;

/**
 * Advanced Path Redirection for Sandbox Isolation.
 * Maps guest data directories to the sandbox root.
 */
static std::string redirect_path(const char* path) {
    if (!path || g_virtual_root.empty()) return path ? path : "";
    
    std::string s_path(path);
    
    // Redirect app data
    std::string target_data = "/data/data/" + g_package_name;
    if (s_path.find(target_data) == 0) {
        return g_virtual_root + "/data" + s_path.substr(target_data.length());
    }
    
    // Redirect external data (Android/data)
    std::string target_ext = "/sdcard/Android/data/" + g_package_name;
    if (s_path.find(target_ext) == 0) {
        return g_virtual_root + "/external" + s_path.substr(target_ext.length());
    }

    // Hide host app presence from guest
    if (s_path.find("/data/data/" + g_host_pkg) == 0) {
        return "/system/etc/hosts"; // Redirect to a benign file
    }
    
    return s_path;
}

// Hook Implementations
int v_open(const char *pathname, int flags, mode_t mode) {
    std::string r_path = redirect_path(pathname);
    return orig_open(r_path.c_str(), flags, mode);
}

int v_openat(int dirfd, const char *pathname, int flags, mode_t mode) {
    std::string r_path = redirect_path(pathname);
    return orig_openat(dirfd, r_path.c_str(), flags, mode);
}

int v_access(const char *pathname, int mode) {
    std::string r_path = redirect_path(pathname);
    return orig_access(r_path.c_str(), mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_NativeHookManager_initHooks(JNIEnv* env, jclass clazz, jstring virtual_root, jstring package_name) {
    const char* v_root = env->GetStringUTFChars(virtual_root, nullptr);
    const char* p_name = env->GetStringUTFChars(package_name, nullptr);
    
    g_virtual_root = v_root;
    g_package_name = p_name;
    
    LOGI("Kernel-Level Isolation booting for: %s", g_package_name.c_str());

    // Use Dobby to apply hooks on libc symbols
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        DobbyHook(dlsym(libc, "open"), (void*)v_open, (void**)&orig_open);
        DobbyHook(dlsym(libc, "openat"), (void*)v_openat, (void**)&orig_openat);
        DobbyHook(dlsym(libc, "access"), (void*)v_access, (void**)&orig_access);
        
        LOGI("Syscall hooks applied successfully.");
        dlclose(libc);
    }

    env->ReleaseStringUTFChars(virtual_root, v_root);
    env->ReleaseStringUTFChars(package_name, p_name);
}
