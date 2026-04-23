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

// syscall pointers
static int (*orig_open)(const char *pathname, int flags, mode_t mode) = nullptr;
static int (*orig_openat)(int dirfd, const char *pathname, int flags, mode_t mode) = nullptr;
static int (*orig_access)(const char *pathname, int mode) = nullptr;

/**
 * Enhanced Path Redirection for BGMI Sandbox Isolation.
 * Redirects both Data and OBB paths.
 */
static std::string redirect_path(const char* path) {
    if (!path || g_virtual_root.empty()) return path ? path : "";
    
    std::string s_path(path);
    
    // 1. Data Redirection (/data/data/...)
    std::string data_target = "/data/data/" + g_package_name;
    if (s_path.find(data_target) == 0) {
        std::string redirected = g_virtual_root + "/data" + s_path.substr(data_target.length());
        LOGI("Redirecting DATA: %s -> %s", path, redirected.c_str());
        return redirected;
    }
    
    // 2. OBB Redirection (/sdcard/Android/obb/...)
    std::string obb_target = "/storage/emulated/0/Android/obb/" + g_package_name;
    std::string obb_target_legacy = "/sdcard/Android/obb/" + g_package_name;
    
    if (s_path.find(obb_target) == 0) {
        std::string redirected = g_virtual_root + "/obb" + s_path.substr(obb_target.length());
        LOGI("Redirecting OBB: %s -> %s", path, redirected.c_str());
        return redirected;
    }
    
    if (s_path.find(obb_target_legacy) == 0) {
        std::string redirected = g_virtual_root + "/obb" + s_path.substr(obb_target_legacy.length());
        LOGI("Redirecting OBB: %s -> %s", path, redirected.c_str());
        return redirected;
    }

    // 3. External Data Redirection
    std::string ext_data = "/storage/emulated/0/Android/data/" + g_package_name;
    if (s_path.find(ext_data) == 0) {
        std::string redirected = g_virtual_root + "/external" + s_path.substr(ext_data.length());
        return redirected;
    }
    
    return s_path;
}

int my_open(const char *pathname, int flags, mode_t mode) {
    return orig_open(redirect_path(pathname).c_str(), flags, mode);
}

int my_openat(int dirfd, const char *pathname, int flags, mode_t mode) {
    return orig_openat(dirfd, redirect_path(pathname).c_str(), flags, mode);
}

int my_access(const char *pathname, int mode) {
    return orig_access(redirect_path(pathname).c_str(), mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_NativeHookManager_initHooks(JNIEnv* env, jclass clazz, jstring virtual_root, jstring package_name) {
    const char* v_root = env->GetStringUTFChars(virtual_root, nullptr);
    const char* p_name = env->GetStringUTFChars(package_name, nullptr);
    g_virtual_root = v_root;
    g_package_name = p_name;

    LOGI("Installing OneCore Kernel-Level Hooks for: %s", p_name);

    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        DobbyHook(dlsym(libc, "open"), (void*)my_open, (void**)&orig_open);
        DobbyHook(dlsym(libc, "openat"), (void*)my_openat, (void**)&orig_openat);
        DobbyHook(dlsym(libc, "access"), (void*)my_access, (void**)&orig_access);
        LOGI("OneCore Native Engine Status: ACTIVE");
        dlclose(libc);
    }

    env->ReleaseStringUTFChars(virtual_root, v_root);
    env->ReleaseStringUTFChars(package_name, p_name);
}
