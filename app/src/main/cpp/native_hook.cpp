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
static int (*orig_execve)(const char *filename, char *const argv[], char *const envp[]) = nullptr;
static void* (*orig_dlopen)(const char* filename, int flags) = nullptr;
static void* (*orig_dlopen_ext)(const char* filename, int flags, const void* extinfo) = nullptr;
static FILE* (*orig_fopen)(const char* filename, const char* mode) = nullptr;
static pid_t (*orig_getppid)() = nullptr;

// --- EGL Hooks ---
static const char* (*orig_glGetString)(int name) = nullptr;
const char* my_glGetString(int name) {
    if (name == 0x1F00) return "Qualcomm"; // GL_VENDOR
    if (name == 0x1F01) return "Adreno (TM) 650"; // GL_RENDERER
    return orig_glGetString(name);
}

/**
 * Path Redirection Engine for BGMI Sandbox.
 */
static std::string redirect_path(const char* path) {
    if (!path || g_virtual_root.empty()) return path ? path : "";
    
    std::string s_path(path);
    
    // Auto-rewrite lib paths if they point to system or host but should be in sandbox
    if (s_path.find("/data/app/") != std::string::npos && s_path.find("/lib/") != std::string::npos) {
         size_t last_slash = s_path.find_last_of("/");
         if (last_slash != std::string::npos) {
             std::string lib_name = s_path.substr(last_slash + 1);
             std::string v_lib_path = g_virtual_root + "/v_lib/" + lib_name;
             struct stat st;
             if (orig_stat && orig_stat(v_lib_path.c_str(), &st) == 0) {
                 return v_lib_path;
             }
         }
    }
    
    // Normalize path: handle double slashes and trailing slashes
    while (s_path.find("//") != std::string::npos) {
        s_path.replace(s_path.find("//"), 2, "/");
    }
    
    // Anti-Detection: Proc Spoofing
    if (s_path == "/proc/self/exe") {
        return "/system/bin/app_process"; // Simulated for anti-detection or return target APK
    }

    if (s_path == "/proc/self/cmdline" || s_path == "/proc/cmdline") {
        std::string fake_cmd = g_virtual_root + "/proc_cmdline";
        FILE* f = fopen(fake_cmd.c_str(), "w");
        if (f) {
            fprintf(f, "%s", g_package_name.c_str());
            fclose(f);
        }
        return fake_cmd;
    }

    if (s_path == "/proc/self/maps" || s_path == "/proc/maps") {
        // We redirect maps to a filtered version that hides 'onecore' and 'v_lib'
        std::string fake_maps = g_virtual_root + "/proc_maps";
        FILE* original = fopen("/proc/self/maps", "r");
        FILE* filtered = fopen(fake_maps.c_str(), "w");
        if (original && filtered) {
            char line[1024];
            while (fgets(line, sizeof(line), original)) {
                if (strstr(line, "onecore") == nullptr && strstr(line, "v_lib") == nullptr) {
                    fputs(line, filtered);
                }
            }
            fclose(original);
            fclose(filtered);
        }
        return fake_maps;
    }

    if (s_path == "/proc/self/status") {
        std::string fake_status = g_virtual_root + "/proc_status";
        FILE* original = fopen("/proc/self/status", "r");
        FILE* filtered = fopen(fake_status.c_str(), "w");
        if (original && filtered) {
            char line[1024];
            while (fgets(line, sizeof(line), original)) {
                if (strstr(line, "TracerPid:") != nullptr) {
                    fputs("TracerPid:\t0\n", filtered);
                } else {
                    fputs(line, filtered);
                }
            }
            fclose(original);
            fclose(filtered);
        }
        return fake_status;
    }

    // 1. Data Dir Redirection (/data/user/0/... or /data/data/...)
    const char* data_roots[] = {"/data/user/0/", "/data/data/"};
    for (const char* root : data_roots) {
        std::string target = std::string(root) + g_package_name;
        if (s_path.compare(0, target.length(), target) == 0) {
            return g_virtual_root + "/data" + s_path.substr(target.length());
        }
    }

    // 2. OBB Dir Redirection (/storage/emulated/0/Android/obb/...)
    const char* obb_roots[] = {
        "/storage/emulated/0/Android/obb/",
        "/sdcard/Android/obb/",
        "/storage/self/primary/Android/obb/",
        "/mnt/shell/emulated/0/Android/obb/",
        "/data/media/0/Android/obb/"
    };
    
    for (const char* root : obb_roots) {
        std::string target = std::string(root) + g_package_name;
        if (s_path.compare(0, target.length(), target) == 0) {
            std::string redirected = g_virtual_root + "/obb" + s_path.substr(target.length());
            // Check if redirected OBB exists, if not, maybe try to access original but hide path
            struct stat st;
            if (orig_stat && orig_stat(redirected.c_str(), &st) == 0) {
                return redirected;
            }
            LOGE("OBB MISSING IN SANDBOX: %s", redirected.c_str());
            return s_path; // Fallback to original if sandbox version missing
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

    // 4. Root Hiding (Busybox, su, Magisk)
    if (s_path.find("/su") != std::string::npos || 
        s_path.find("/magisk") != std::string::npos ||
        s_path.find("busybox") != std::string::npos) {
        return "/system/bin/onecore_missing";
    }

    return s_path;
}

int my_open(const char *pathname, int flags, mode_t mode) {
    std::string r_path = redirect_path(pathname);
    // Log OBB access for debugging
    if (r_path.find("/obb") != std::string::npos) {
        LOGI("HOOK: OBB Open Access -> %s", r_path.c_str());
    }
    return orig_open(r_path.c_str(), flags, mode);
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

int my_execve(const char *filename, char *const argv[], char *const envp[]) {
    // Block the game from launching child processes that might detect us
    if (filename && strstr(filename, "su")) return -1;
    return orig_execve(filename, argv, envp);
}

void* my_dlopen_ext(const char* filename, int flags, const void* extinfo) {
    if (filename) {
        std::string path = redirect_path(filename);
        return orig_dlopen_ext(path.c_str(), flags, extinfo);
    }
    return orig_dlopen_ext(filename, flags, extinfo);
}

void* my_dlopen(const char* filename, int flags) {
    if (filename) {
        std::string path = redirect_path(filename);
        if (path != filename) {
            LOGI("HOOK: dlopen redirected %s -> %s", filename, path.c_str());
        }
        return orig_dlopen(path.c_str(), flags);
    }
    return orig_dlopen(filename, flags);
}

FILE* my_fopen(const char* filename, const char* mode) {
    if (filename) {
        std::string path = redirect_path(filename);
        return orig_fopen(path.c_str(), mode);
    }
    return orig_fopen(filename, mode);
}

pid_t my_getppid() {
    return 1; // System process parent
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
        DobbyHook(dlsym(libc, "fopen"), (void*)my_fopen, (void**)&orig_fopen);
        DobbyHook(dlsym(libc, "open"), (void*)my_open, (void**)&orig_open);
        DobbyHook(dlsym(libc, "openat"), (void*)my_openat, (void**)&orig_openat);
        DobbyHook(dlsym(libc, "access"), (void*)my_access, (void**)&orig_access);
        DobbyHook(dlsym(libc, "faccessat"), (void*)my_faccessat, (void**)&orig_faccessat);
        DobbyHook(dlsym(libc, "stat"), (void*)my_stat, (void**)&orig_stat);
        DobbyHook(dlsym(libc, "lstat"), (void*)my_lstat, (void**)&orig_lstat);
        DobbyHook(dlsym(libc, "fstatat"), (void*)my_fstatat, (void**)&orig_fstatat);
        DobbyHook(dlsym(libc, "readlink"), (void*)my_readlink, (void**)&orig_readlink);
        DobbyHook(dlsym(libc, "opendir"), (void*)my_opendir, (void**)&orig_opendir);
        DobbyHook(dlsym(libc, "execve"), (void*)my_execve, (void**)&orig_execve);
        DobbyHook(dlsym(libc, "getppid"), (void*)my_getppid, (void**)&orig_getppid);
        
        void* opendir2_ptr = dlsym(libc, "__opendir2");
        if (opendir2_ptr) DobbyHook(opendir2_ptr, (void*)my_opendir2, (void**)&orig_opendir2);
        
        LOGI("Extended Native Hooks (IO + PROC + EXEC) INSTALLED.");
        dlclose(libc);
    }

    void* libdl = dlopen("libdl.so", RTLD_NOW);
    if (libdl) {
        void* dlopen_ptr = dlsym(libdl, "dlopen");
        if (dlopen_ptr) DobbyHook(dlopen_ptr, (void*)my_dlopen, (void**)&orig_dlopen);
        
        void* dlopen_ext_ptr = dlsym(libdl, "android_dlopen_ext");
        if (dlopen_ext_ptr) DobbyHook(dlopen_ext_ptr, (void*)my_dlopen_ext, (void**)&orig_dlopen_ext);
        dlclose(libdl);
    }
    
    void* libgles = dlopen("libGLESv2.so", RTLD_NOW);
    if (libgles) {
        DobbyHook(dlsym(libgles, "glGetString"), (void*)my_glGetString, (void**)&orig_glGetString);
        dlclose(libgles);
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
static uid_t g_fake_uid = 10100;
static uid_t (*orig_getuid)() = nullptr;
static uid_t (*orig_geteuid)() = nullptr;

uid_t my_getuid() {
    return g_fake_uid;
}

uid_t my_geteuid() {
    return g_fake_uid;
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_core_UidSpoofing_applyNative(JNIEnv* env, jclass clazz, jint fakeUid) {
    g_fake_uid = (uid_t)fakeUid;
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        DobbyHook(dlsym(libc, "getuid"), (void*)my_getuid, (void**)&orig_getuid);
        DobbyHook(dlsym(libc, "geteuid"), (void*)my_geteuid, (void**)&orig_geteuid);
        // Some games use getcallinguid from binder, but that's usually at service level.
        // We'll hook getuid and geteuid which are common in native layer.
        LOGI("Native UID Spoof applied: %d", fakeUid);
        dlclose(libc);
    }
}
