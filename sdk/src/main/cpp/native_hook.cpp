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
#include "BinderHook.h"
#include "Hook/UnixFileSystemHook.h"
#include "Hook/RuntimeHook.h"
#include "JniHook/JniHook.h"
#include "Utils/Stealth.h"
#include "Utils/EnvironmentProtector.h"
#include "Utils/KernelShield.h"
#include "Utils/HiddenApiBypass.h"
#include "Utils/ModuleScanner.h"
#include "Hook/DexFileHook.h"
#include "Hook/SocketHook.h"
#include "Hook/RenderingHook.h"
#include "KittyMemory/KittyMemory.h"
#include "Utils/RecursionGuard.h"

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

// Recursion guard is handled via Utils/RecursionGuard.h

// --- EGL Hooks ---
static const char* (*orig_glGetString)(int name) = nullptr;
const char* my_glGetString(int name) {
    if (g_in_hook) return orig_glGetString(name);
    g_in_hook = true;
    const char* res;
    if (name == 0x1F00) res = "Qualcomm"; // GL_VENDOR
    else if (name == 0x1F01) res = "Adreno (TM) 650"; // GL_RENDERER
    else res = orig_glGetString(name);
    g_in_hook = false;
    return res;
}

/**
 * Path Redirection Engine for BGMI Sandbox.
 */
static std::string redirect_path(const char* path) {
    if (g_in_hook || !path || g_virtual_root.empty()) return path ? path : "";
    
    std::string s_path(path);
    
    // Safety check: is it a system critical path we shouldn't touch?
    if (s_path.size() < 2 || s_path[0] != '/') return s_path;
    
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
        return "/system/bin/app_process"; 
    }

    if (s_path == "/proc/self/cmdline" || s_path == "/proc/cmdline") {
        std::string fake_cmd = g_virtual_root + "/proc_cmdline";
        // Use original fopen to prevent recursion
        FILE* f = orig_fopen ? orig_fopen(fake_cmd.c_str(), "w") : fopen(fake_cmd.c_str(), "w");
        if (f) {
            fprintf(f, "%s", g_package_name.c_str());
            fclose(f);
        }
        return fake_cmd;
    }

    if (s_path == "/proc/self/maps" || s_path == "/proc/maps") {
        std::string fake_maps = g_virtual_root + "/proc_maps";
        FILE* original = orig_fopen ? orig_fopen("/proc/self/maps", "r") : fopen("/proc/self/maps", "r");
        FILE* filtered = orig_fopen ? orig_fopen(fake_maps.c_str(), "w") : fopen(fake_maps.c_str(), "w");
        if (original && filtered) {
            char line[1024];
            while (fgets(line, sizeof(line), original)) {
                // Hide OneCore and Virtual spaces from memory maps
                if (strstr(line, "onecore") == nullptr && strstr(line, "v_lib") == nullptr && strstr(line, "libdobby") == nullptr) {
                    fputs(line, filtered);
                }
            }
            if (original) fclose(original);
            if (filtered) fclose(filtered);
        }
        return fake_maps;
    }

    if (s_path == "/proc/self/status") {
        std::string fake_status = g_virtual_root + "/proc_status";
        FILE* original = orig_fopen ? orig_fopen("/proc/self/status", "r") : fopen("/proc/self/status", "r");
        FILE* filtered = orig_fopen ? orig_fopen(fake_status.c_str(), "w") : fopen(fake_status.c_str(), "w");
        if (original && filtered) {
            char line[1024];
            while (fgets(line, sizeof(line), original)) {
                if (strstr(line, "TracerPid:") != nullptr) {
                    fputs("TracerPid:\t0\n", filtered);
                } else if (strstr(line, "Uid:") != nullptr) {
                    fputs("Uid:\t1000\t1000\t1000\t1000\n", filtered); // Fake system UID
                } else {
                    fputs(line, filtered);
                }
            }
            if (original) fclose(original);
            if (filtered) fclose(filtered);
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
            struct stat st;
            if (orig_stat && orig_stat(redirected.c_str(), &st) == 0) {
                return redirected;
            }
            std::string fallback = g_virtual_root + "/data/files/obb" + s_path.substr(target.length());
            if (orig_stat && orig_stat(fallback.c_str(), &st) == 0) {
                return fallback;
            }
            return s_path; 
        }
    }

    // 3. External Data Redirection
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

    // 4. Root Hiding 
    if (s_path.find("/su") != std::string::npos || 
        s_path.find("/magisk") != std::string::npos ||
        s_path.find("busybox") != std::string::npos) {
        return "/system/bin/onecore_missing";
    }

    return s_path;
}

// Updated Hook functions with guard
static int my_open(const char *pathname, int flags, mode_t mode) {
    if (g_in_hook) return orig_open(pathname, flags, mode);
    g_in_hook = true;
    std::string r_path = redirect_path(pathname);
    // Log OBB access for debugging
    if (r_path.find("/obb") != std::string::npos) {
        LOGI("HOOK: OBB Open Access -> %s", r_path.c_str());
    }
    int res = orig_open(r_path.c_str(), flags, mode);
    g_in_hook = false;
    return res;
}

int my_openat(int dirfd, const char *pathname, int flags, mode_t mode) {
    if (g_in_hook) return orig_openat(dirfd, pathname, flags, mode);
    g_in_hook = true;
    int res;
    if (pathname && pathname[0] == '/') {
        res = orig_openat(dirfd, redirect_path(pathname).c_str(), flags, mode);
    } else {
        res = orig_openat(dirfd, pathname, flags, mode);
    }
    g_in_hook = false;
    return res;
}

static int my_access(const char *pathname, int mode) {
    if (g_in_hook) return orig_access(pathname, mode);
    g_in_hook = true;
    int res = orig_access(redirect_path(pathname).c_str(), mode);
    g_in_hook = false;
    return res;
}

int my_faccessat(int dirfd, const char *pathname, int mode, int flags) {
    if (g_in_hook) return orig_faccessat(dirfd, pathname, mode, flags);
    g_in_hook = true;
    int res;
    if (pathname && pathname[0] == '/') {
        res = orig_faccessat(dirfd, redirect_path(pathname).c_str(), mode, flags);
    } else {
        res = orig_faccessat(dirfd, pathname, mode, flags);
    }
    g_in_hook = false;
    return res;
}

int my_stat(const char *pathname, struct stat *buf) {
    if (g_in_hook) return orig_stat(pathname, buf);
    g_in_hook = true;
    int res = orig_stat(redirect_path(pathname).c_str(), buf);
    g_in_hook = false;
    return res;
}

int my_lstat(const char *pathname, struct stat *buf) {
    if (g_in_hook) return orig_lstat(pathname, buf);
    g_in_hook = true;
    int res = orig_lstat(redirect_path(pathname).c_str(), buf);
    g_in_hook = false;
    return res;
}

int my_fstatat(int dirfd, const char *pathname, struct stat *buf, int flags) {
    if (g_in_hook) return orig_fstatat(dirfd, pathname, buf, flags);
    g_in_hook = true;
    int res;
    if (pathname && pathname[0] == '/') {
        res = orig_fstatat(dirfd, redirect_path(pathname).c_str(), buf, flags);
    } else {
        res = orig_fstatat(dirfd, pathname, buf, flags);
    }
    g_in_hook = false;
    return res;
}

ssize_t my_readlink(const char *pathname, char *buf, size_t bufsiz) {
    if (g_in_hook) return orig_readlink(pathname, buf, bufsiz);
    g_in_hook = true;
    ssize_t res = orig_readlink(redirect_path(pathname).c_str(), buf, bufsiz);
    g_in_hook = false;
    return res;
}

void* my_opendir(const char *name) {
    if (g_in_hook) return orig_opendir(name);
    g_in_hook = true;
    void* res = orig_opendir(redirect_path(name).c_str());
    g_in_hook = false;
    return res;
}

void* my_opendir2(const char *name, int flags) {
    if (g_in_hook) return orig_opendir2(name, flags);
    g_in_hook = true;
    void* res = orig_opendir2(redirect_path(name).c_str(), flags);
    g_in_hook = false;
    return res;
}

int my_execve(const char *filename, char *const argv[], char *const envp[]) {
    if (g_in_hook) return orig_execve(filename, argv, envp);
    g_in_hook = true;
    int res;
    // Block the game from launching child processes that might detect us
    if (filename && strstr(filename, "su")) {
        res = -1;
    } else {
        res = orig_execve(filename, argv, envp);
    }
    g_in_hook = false;
    return res;
}

void* my_dlopen_ext(const char* filename, int flags, const void* extinfo) {
    if (g_in_hook) return orig_dlopen_ext(filename, flags, extinfo);
    g_in_hook = true;
    void* res;
    if (filename) {
        std::string path = redirect_path(filename);
        res = orig_dlopen_ext(path.c_str(), flags, extinfo);
    } else {
        res = orig_dlopen_ext(filename, flags, extinfo);
    }
    g_in_hook = false;
    return res;
}

void* my_dlopen(const char* filename, int flags) {
    if (g_in_hook) return orig_dlopen(filename, flags);
    g_in_hook = true;
    void* res;
    if (filename) {
        std::string path = redirect_path(filename);
        if (path != filename) {
            LOGI("HOOK: dlopen redirected %s -> %s", filename, path.c_str());
        }
        res = orig_dlopen(path.c_str(), flags);
    } else {
        res = orig_dlopen(filename, flags);
    }
    g_in_hook = false;
    return res;
}

FILE* my_fopen(const char* filename, const char* mode) {
    if (g_in_hook) return orig_fopen(filename, mode);
    g_in_hook = true;
    FILE* res;
    if (filename) {
        std::string path = redirect_path(filename);
        res = orig_fopen(path.c_str(), mode);
    } else {
        res = orig_fopen(filename, mode);
    }
    g_in_hook = false;
    return res;
}

pid_t my_getppid() {
    return 1; // System process parent
}

// --- JNI Implementation for NativeHookManager ---

static void safe_hook(void* target, void* replace, void** origin, const char* name) {
    if (!target) {
        LOGE("Failed to hook %s: target is null", name);
        return;
    }
    if (DobbyHook(target, replace, origin) == 0) {
        LOGI("Successfully hooked: %s at %p", name, target);
    } else {
        LOGE("FAILED to hook: %s at %p", name, target);
    }
}

static bool g_init_done = false;

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_NativeHookManager_initHooks(JNIEnv* env, jclass clazz, jstring virtual_root, jstring package_name) {
    if (g_init_done) {
        LOGI("initHooks: Already initialized, skipping.");
        return;
    }
    g_init_done = true;

    if (!virtual_root || !package_name) {
        LOGE("initHooks: null arguments provided");
        return;
    }
    const char* v_root = env->GetStringUTFChars(virtual_root, nullptr);
    const char* p_name = env->GetStringUTFChars(package_name, nullptr);
    if (!v_root || !p_name) {
        LOGE("initHooks: failed to get UTF chars");
        return;
    }
    g_virtual_root = v_root;
    g_package_name = p_name;

    LOGI(">>> Initializing OneCore Native Engine for: %s <<<", p_name);

    LOGI("Phase 1: Security Bypass");
    OneCore::enableStealthMode();
    OneCore::bypassHiddenApi(env);

    LOGI("Phase 2: Modular Internal Hooks");
    OneCore::installFileSystemHooks();
    OneCore::installRuntimeHooks();
    OneCore::installJniHooks(env);
    OneCore::installEnvironmentProtector();
    OneCore::installDexHooks();
    OneCore::installKernelShield();
    OneCore::installSocketHooks();
    OneCore::installRenderingHooks();

    LOGI("Phase 3: Standard Library Hooks (LibC)");
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        safe_hook(dlsym(libc, "fopen"), (void*)my_fopen, (void**)&orig_fopen, "fopen");
        safe_hook(dlsym(libc, "open"), (void*)my_open, (void**)&orig_open, "open");
        safe_hook(dlsym(libc, "openat"), (void*)my_openat, (void**)&orig_openat, "openat");
        safe_hook(dlsym(libc, "access"), (void*)my_access, (void**)&orig_access, "access");
        safe_hook(dlsym(libc, "faccessat"), (void*)my_faccessat, (void**)&orig_faccessat, "faccessat");
        safe_hook(dlsym(libc, "stat"), (void*)my_stat, (void**)&orig_stat, "stat");
        safe_hook(dlsym(libc, "lstat"), (void*)my_lstat, (void**)&orig_lstat, "lstat");
        safe_hook(dlsym(libc, "fstatat"), (void*)my_fstatat, (void**)&orig_fstatat, "fstatat");
        safe_hook(dlsym(libc, "readlink"), (void*)my_readlink, (void**)&orig_readlink, "readlink");
        safe_hook(dlsym(libc, "opendir"), (void*)my_opendir, (void**)&orig_opendir, "opendir");
        safe_hook(dlsym(libc, "execve"), (void*)my_execve, (void**)&orig_execve, "execve");
        safe_hook(dlsym(libc, "getppid"), (void*)my_getppid, (void**)&orig_getppid, "getppid");
        safe_hook(dlsym(libc, "__opendir2"), (void*)my_opendir2, (void**)&orig_opendir2, "__opendir2");
        dlclose(libc);
    } else {
        LOGE("CRITICAL: Could not open libc.so!");
    }

    LOGI("Phase 4: Dynamic Linker Hooks (LibDL)");
    void* libdl = dlopen("libdl.so", RTLD_NOW);
    if (libdl) {
        safe_hook(dlsym(libdl, "dlopen"), (void*)my_dlopen, (void**)&orig_dlopen, "dlopen");
        safe_hook(dlsym(libdl, "android_dlopen_ext"), (void*)my_dlopen_ext, (void**)&orig_dlopen_ext, "android_dlopen_ext");
        dlclose(libdl);
    }
    
    LOGI("Phase 5: Rendering Hooks (GLES)");
    void* libgles = dlopen("libGLESv2.so", RTLD_NOW);
    if (libgles) {
        safe_hook(dlsym(libgles, "glGetString"), (void*)my_glGetString, (void**)&orig_glGetString, "glGetString");
        dlclose(libgles);
    }

    LOGI(">>> Native Engine Initialization COMPLETE for: %s <<<", p_name);

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
    if (buffer == nullptr) return JNI_FALSE;
    
    // Use mprotect to ensure memory is writable (typical in Unreal Engine hacks)
    long pagesize = sysconf(_SC_PAGESIZE);
    void* page = (void*)(addr & ~(pagesize - 1));
    if (mprotect(page, pagesize * 2, PROT_READ | PROT_WRITE | PROT_EXEC) == 0) {
        memcpy((void*)addr, buffer, size);
        env->ReleaseByteArrayElements(data, buffer, JNI_ABORT);
        return JNI_TRUE;
    } else {
        LOGE("writeMemoryNative: mprotect failed: %s", strerror(errno));
        env->ReleaseByteArrayElements(data, buffer, JNI_ABORT);
        return JNI_FALSE;
    }
}

// --- UID Spoofing Hook ---
static uid_t g_fake_uid = 10100;
static uid_t (*orig_getuid)() = nullptr;
static uid_t (*orig_geteuid)() = nullptr;
static uid_t (*orig_getgid)() = nullptr;

uid_t my_getuid() {
    if (g_in_hook) return orig_getuid();
    return g_fake_uid;
}

uid_t my_geteuid() {
    if (g_in_hook) return orig_geteuid();
    return g_fake_uid;
}

uid_t my_getgid() {
    if (g_in_hook) return orig_getgid();
    return 2000; // Fake shell/system group
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_core_UidSpoofing_applyNative(JNIEnv* env, jclass clazz, jint fakeUid) {
    g_fake_uid = (uid_t)fakeUid;
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        DobbyHook(dlsym(libc, "getuid"), (void*)my_getuid, (void**)&orig_getuid);
        DobbyHook(dlsym(libc, "geteuid"), (void*)my_geteuid, (void**)&orig_geteuid);
        DobbyHook(dlsym(libc, "getgid"), (void*)my_getgid, (void**)&orig_getgid);
        LOGI("Native UID Spoof applied: %d", fakeUid);
        dlclose(libc);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_NativeHook_installBinderHook(JNIEnv* env, jobject thiz) {
    OneCore::installBinderHooks();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_onecore_sdk_MemoryReader_findModuleBase(JNIEnv* env, jobject thiz, jstring module_name) {
    const char* name = env->GetStringUTFChars(module_name, nullptr);
    auto info = OneCore::findModule(name);
    env->ReleaseStringUTFChars(module_name, name);
    return (jlong)info.base;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_onecore_sdk_MemoryReader_scanSignature(JNIEnv* env, jobject thiz, jlong start, jlong end, jstring signature) {
    const char* sig = env->GetStringUTFChars(signature, nullptr);
    uintptr_t result = KittyMemory::findSignature((uintptr_t)start, (uintptr_t)end, sig);
    env->ReleaseStringUTFChars(signature, sig);
    return (jlong)result;
}
