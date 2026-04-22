#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include "dobby.h"

#define TAG "OneCoreNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::string g_virtual_root;
static std::string g_package_name;

// Function pointers for original syscalls
static int (*orig_open)(const char *pathname, int flags, mode_t mode) = nullptr;
static int (*orig_openat)(int dirfd, const char *pathname, int flags, mode_t mode) = nullptr;

/**
 * Path redirection logic for sandbox isolation.
 */
static std::string redirect_path(const char* path) {
    if (!path || g_virtual_root.empty()) return path ? path : "";
    
    std::string s_path(path);
    std::string target = "/data/data/" + g_package_name;
    
    if (s_path.find(target) == 0) {
        return g_virtual_root + "/data" + s_path.substr(target.length());
    }
    
    return s_path;
}

// Hooked functions
int my_open(const char *pathname, int flags, mode_t mode) {
    std::string new_path = redirect_path(pathname);
    return orig_open(new_path.c_str(), flags, mode);
}

int my_openat(int dirfd, const char *pathname, int flags, mode_t mode) {
    std::string new_path = redirect_path(pathname);
    return orig_openat(dirfd, new_path.c_str(), flags, mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_io_IORedirector_initNative(JNIEnv* env, jclass clazz, jstring virtual_root, jstring package_name) {
    const char* v_root = env->GetStringUTFChars(virtual_root, nullptr);
    const char* p_name = env->GetStringUTFChars(package_name, nullptr);
    
    g_virtual_root = v_root;
    g_package_name = p_name;
    
    LOGI("Initializing Native Hooks for: %s", g_package_name.c_str());

    // Applying hooks via Dobby
    void* open_addr = dlsym(RTLD_DEFAULT, "open");
    void* openat_addr = dlsym(RTLD_DEFAULT, "openat");

    if (open_addr) {
        DobbyHook(open_addr, (void*)my_open, (void**)&orig_open);
        LOGI("Hooked open at %p", open_addr);
    }
    
    if (openat_addr) {
        DobbyHook(openat_addr, (void*)my_openat, (void**)&orig_openat);
        LOGI("Hooked openat at %p", openat_addr);
    }

    env->ReleaseStringUTFChars(virtual_root, v_root);
    env->ReleaseStringUTFChars(package_name, p_name);
}
