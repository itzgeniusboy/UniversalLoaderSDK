#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>

#define TAG "OneCore-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static std::string g_virtualRoot;
static std::string g_packageName;

// Original function character pointers
static int (*orig_open)(const char *pathname, int flags, mode_t mode) = nullptr;
static int (*orig_stat)(const char *pathname, struct stat *buf) = nullptr;

/**
 * Android 14+ IO Redirection Logic.
 * Captures file access and redirects to sandbox.
 */
const char* redirect_path(const char* path) {
    if (path == nullptr) return nullptr;
    
    std::string s_path(path);
    std::string target = "/data/data/" + g_packageName;
    
    if (s_path.find(target) == 0) {
        static std::string redirected;
        redirected = g_virtualRoot + "/data" + s_path.substr(target.length());
        return redirected.c_str();
    }
    
    return path;
}

// Hooked Functions
int hooked_open(const char *pathname, int flags, mode_t mode) {
    const char* newPath = redirect_path(pathname);
    return orig_open(newPath, flags, mode);
}

int hooked_stat(const char *pathname, struct stat *buf) {
    const char* newPath = redirect_path(pathname);
    return orig_stat(newPath, buf);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_IORedirector_initNativeHooks(JNIEnv* env, jclass clazz, jstring virtualRoot, jstring packageName) {
    const char* vRoot = env->GetStringUTFChars(virtualRoot, nullptr);
    const char* pName = env->GetStringUTFChars(packageName, nullptr);
    
    g_virtualRoot = vRoot;
    g_packageName = pName;
    
    LOGI("Native Hooks Initializing for: %s", g_packageName.c_str());

    // Using dlsym for baseline redirection (Dobby integration would replace this with DobbyHook)
    orig_open = (int (*)(const char*, int, mode_t))dlsym(RTLD_NEXT, "open");
    orig_stat = (int (*)(const char*, struct stat*))dlsym(RTLD_NEXT, "stat");

    env->ReleaseStringUTFChars(virtualRoot, vRoot);
    env->ReleaseStringUTFChars(packageName, pName);
}
