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
static int (*orig_stat)(const char *pathname, struct stat *buf) = nullptr;

static std::string redirect_path(const char* path) {
    if (!path || g_virtual_root.empty()) return path ? path : "";
    std::string s_path(path);
    std::string target = "/data/data/" + g_package_name;
    if (s_path.find(target) == 0) {
        return g_virtual_root + "/data" + s_path.substr(target.length());
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

    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        DobbyHook(dlsym(libc, "open"), (void*)my_open, (void**)&orig_open);
        DobbyHook(dlsym(libc, "openat"), (void*)my_openat, (void**)&orig_openat);
        DobbyHook(dlsym(libc, "access"), (void*)my_access, (void**)&orig_access);
        LOGI("OneCore Native Hooks Installed.");
        dlclose(libc);
    }

    env->ReleaseStringUTFChars(virtual_root, v_root);
    env->ReleaseStringUTFChars(package_name, p_name);
}
