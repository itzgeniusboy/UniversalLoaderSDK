#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <EGL/egl.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <time.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <GLES2/gl2.h>
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
#include "KittyMemory/KittyMemory.h"
#include "Utils/RecursionGuard.h"
#include <stdarg.h>
#include <sys/ptrace.h>
#include <sys/system_properties.h>

#include <signal.h>
#include <sys/syscall.h>
#include <time.h>

#define TAG "OneCore-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static const char* get_egl_error_name(EGLint err) {
    switch (err) {
        case EGL_SUCCESS: return "EGL_SUCCESS";
        case EGL_NOT_INITIALIZED: return "EGL_NOT_INITIALIZED";
        case EGL_BAD_ACCESS: return "EGL_BAD_ACCESS";
        case EGL_BAD_ALLOC: return "EGL_BAD_ALLOC";
        case EGL_BAD_ATTRIBUTE: return "EGL_BAD_ATTRIBUTE";
        case EGL_BAD_CONFIG: return "EGL_BAD_CONFIG";
        case EGL_BAD_CONTEXT: return "EGL_BAD_CONTEXT";
        case EGL_BAD_CURRENT_SURFACE: return "EGL_BAD_CURRENT_SURFACE";
        case EGL_BAD_DISPLAY: return "EGL_BAD_DISPLAY";
        case EGL_BAD_MATCH: return "EGL_BAD_MATCH";
        case EGL_BAD_NATIVE_PIXMAP: return "EGL_BAD_NATIVE_PIXMAP";
        case EGL_BAD_NATIVE_WINDOW: return "EGL_BAD_NATIVE_WINDOW";
        case EGL_BAD_PARAMETER: return "EGL_BAD_PARAMETER";
        case EGL_BAD_SURFACE: return "EGL_BAD_SURFACE";
        case EGL_CONTEXT_LOST: return "EGL_CONTEXT_LOST";
        default: return "UNKNOWN";
    }
}

static void check_egl_error(const char* op) {
    EGLint err = eglGetError();
    if (err != EGL_SUCCESS) {
        LOGE("EGL ERROR after %s: %s (0x%X)", op, get_egl_error_name(err), err);
    }
}

static void safe_hook(void* target, void* replace, void** origin, const char* name);

// --- Vulkan Hook Forward Declarations ---
static VkResult my_vkCreateAndroidSurfaceKHR(VkInstance instance, const VkAndroidSurfaceCreateInfoKHR* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkSurfaceKHR* pSurface);
static VkResult my_vkQueuePresentKHR(VkQueue queue, const VkPresentInfoKHR* pPresentInfo);
static PFN_vkVoidFunction my_vkGetInstanceProcAddr(VkInstance instance, const char* pName);
static PFN_vkVoidFunction my_vkGetDeviceProcAddr(VkDevice device, const char* pName);

// --- Vulkan Original Function Pointers ---
static PFN_vkCreateAndroidSurfaceKHR orig_vkCreateAndroidSurfaceKHR = nullptr;
static PFN_vkQueuePresentKHR orig_vkQueuePresentKHR = nullptr;
static PFN_vkGetInstanceProcAddr orig_vkGetInstanceProcAddr = nullptr;
static PFN_vkGetDeviceProcAddr orig_vkGetDeviceProcAddr = nullptr;

static int g_last_step = 0;
void log_step(int step, const char* msg) {
    g_last_step = step;
    LOGI(">>> STEP %d: %s <<<", step, msg);
}

// Signal handler for crash tracing
void signal_handler(int sig, siginfo_t* info, void* context) {
    LOGE("!!! CRITICAL NATIVE CRASH !!!");
    LOGE("Signal: %d, Fault Address: %p", sig, info->si_addr);
    LOGE("Last successful STEP marker: %d", g_last_step);
    LOGE("Thread ID: %d", (int)gettid());
    
    // Default action to allow system to handle it (generate tombstone)
    struct sigaction sa;
    sa.sa_handler = SIG_DFL;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(sig, &sa, NULL);
    raise(sig);
}

void install_signal_handlers() {
    struct sigaction sa;
    sa.sa_sigaction = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_SIGINFO;

    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
    LOGI("Crash tracing signal handlers installed.");
}

static std::string g_virtual_root;
static std::string g_package_name;
static uid_t g_fake_uid = 10100;

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
static pid_t (*orig_fork)() = nullptr;
static long (*orig_ptrace)(int request, pid_t pid, void* addr, void* data) = nullptr;
static int (*orig_system_property_get)(const char* name, char* value) = nullptr;
static uid_t (*orig_getuid)() = nullptr;
static uid_t (*orig_geteuid)() = nullptr;
static uid_t (*orig_getgid)() = nullptr;

// Recursion guard is handled via Utils/RecursionGuard.h

// --- Rendering Hooks ---
static int g_egl_frame_count = 0;
static int g_vk_frame_count = 0;
static int g_buffer_enqueue_count = 0;
static int g_buffer_dequeue_count = 0;
static bool g_hwc_bypass_detected = false;
extern thread_local bool g_in_hook;

static int (*orig_Surface_dequeueBuffer)(void* self, void** buffer, int* fenceFd) = nullptr;
static int (*orig_Surface_queueBuffer)(void* self, void* buffer, int fenceFd) = nullptr;

int my_Surface_dequeueBuffer(void* self, void** buffer, int* fenceFd) {
    if (g_in_hook) return orig_Surface_dequeueBuffer(self, buffer, fenceFd);
    g_in_hook = true;
    g_buffer_dequeue_count++;
    int res = orig_Surface_dequeueBuffer(self, buffer, fenceFd);
    g_in_hook = false;
    return res;
}

int my_Surface_queueBuffer(void* self, void* buffer, int fenceFd) {
    if (g_in_hook) return orig_Surface_queueBuffer(self, buffer, fenceFd);
    g_in_hook = true;
    g_buffer_enqueue_count++;
    
    // Check if fence is valid
    if (fenceFd < 0 && fenceFd != -1) {
        LOGW("DIAG: INVALID FENCE DETECTED: %d", fenceFd);
        LOGI("ISSUE: SYNC FAILURE");
    }

    int res = orig_Surface_queueBuffer(self, buffer, fenceFd);
    g_in_hook = false;
    return res;
}

static void* (*orig_ANativeWindow_fromSurface)(void* env, jobject surface) = nullptr;
static void (*orig_ANativeWindow_acquire)(void* window) = nullptr;
static void (*orig_ANativeWindow_release)(void* window) = nullptr;
static int (*orig_ANativeWindow_setBuffersGeometry)(void* window, int32_t width, int32_t height, int32_t format) = nullptr;
static int (*orig_ANativeWindow_unlockAndPost)(void* window) = nullptr;
static void (*orig_ASurfaceTransaction_setBuffer)(void* transaction, void* surfaceControl, void* buffer, int fenceFd) = nullptr;
static void (*orig_ASurfaceTransaction_apply)(void* transaction) = nullptr;

static EGLSurface (*orig_eglCreateWindowSurface)(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint *attrib_list) = nullptr;
static EGLBoolean (*orig_eglMakeCurrent)(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) = nullptr;

static EGLBoolean (*orig_eglSwapBuffers)(EGLDisplay dpy, EGLSurface surface) = nullptr;

static EGLNativeWindowType g_target_window = nullptr;

static void print_rendering_diagnosis() {
    static int last_diag_time = 0;
    int curr_time = (int)time(NULL);
    if (curr_time - last_diag_time < 5) return;
    last_diag_time = curr_time;

    LOGI("---------------- RENDERING DIAGNOSIS ----------------");
    if (g_target_window == nullptr) {
        LOGW("DIAG: ISSUE - NO TARGET SURFACE SET (Native layer has no output window)");
    } else {
        LOGI("DIAG: SUCCESS - Target Redirection Window is: %p", g_target_window);
    }
    
    LOGI("DIAG: EGL Frames Count: %d", g_egl_frame_count);
    LOGI("DIAG: Vulkan Frames Count: %d", g_vk_frame_count);
    LOGI("DIAG: Buffer Enqueue Count: %d", g_buffer_enqueue_count);
    LOGI("DIAG: Buffer Dequeue Count: %d", g_buffer_dequeue_count);
    
    if (g_hwc_bypass_detected) {
        LOGW("DIAG: HWC DIRECT RENDERING DETECTED");
        LOGI("ISSUE: HWC BYPASS");
    }

    if (g_egl_frame_count > 0) {
        LOGI("DIAG: ACTIVE BACKEND: EGL");
    } else if (g_vk_frame_count > 0) {
        LOGI("DIAG: ACTIVE BACKEND: VULKAN");
    } else if (g_buffer_enqueue_count > 0) {
        LOGI("DIAG: ACTIVE BACKEND: DIRECT BUFFER QUEUE (HWC/Native)");
    } else {
        LOGW("DIAG: ISSUE - NO RENDERING DETECTED (0 frames detected)");
        LOGI("ISSUE: NO FRAME OUTPUT");
        return;
    }
    
    if (g_buffer_dequeue_count > g_buffer_enqueue_count + 5) {
        LOGE("DIAG: BUFFER LEAK DETECTED: Dequeued > Enqueued");
        LOGI("ISSUE: BUFFER NOT ACQUIRED");
    }

    if ((g_egl_frame_count > 0 || g_vk_frame_count > 0 || g_buffer_enqueue_count > 0) && g_target_window != nullptr) {
        LOGI("DIAG: REDIRECTION STATUS: SUCCESSFUL (Data should be flowing)");
        LOGI("RENDER TARGET: VIRTUAL DISPLAY");
    } else {
        LOGE("DIAG: REDIRECTION STATUS: FAILED (Frames rendered but no target set)");
        LOGI("RENDER TARGET: PHYSICAL DISPLAY (Default)");
        LOGI("ISSUE: RENDERING TO WRONG DISPLAY");
    }
    LOGI("-----------------------------------------------------");
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_NativeHookManager_setTargetSurface(JNIEnv* env, jclass clazz, jobject surface) {
    if (surface == nullptr) {
        g_target_window = nullptr;
        return;
    }
    
    if (orig_ANativeWindow_fromSurface != nullptr) {
        g_target_window = (EGLNativeWindowType)orig_ANativeWindow_fromSurface(env, surface);
    } else {
        // Early call before hooks are installed - use dlopen/dlsym to be safe
        void* libandroid = dlopen("libandroid.so", RTLD_NOW);
        if (libandroid) {
            auto func = (void* (*)(void*, jobject))dlsym(libandroid, "ANativeWindow_fromSurface");
            if (func) g_target_window = (EGLNativeWindowType)func(env, surface);
            dlclose(libandroid);
        }
    }
    LOGI("Native target window set: %p", g_target_window);
}

int my_ANativeWindow_setBuffersGeometry(void* window, int32_t width, int32_t height, int32_t format) {
    if (g_in_hook) return orig_ANativeWindow_setBuffersGeometry(window, width, height, format);
    g_in_hook = true;
    LOGI("HOOK: ANativeWindow_setBuffersGeometry(win=%p, w=%d, h=%d, fmt=%d)", window, width, height, format);
    int res = orig_ANativeWindow_setBuffersGeometry(window, width, height, format);
    g_in_hook = false;
    return res;
}

int my_ANativeWindow_unlockAndPost(void* window) {
    if (g_in_hook) return orig_ANativeWindow_unlockAndPost(window);
    g_in_hook = true;
    
    static int canvas_frame_count = 0;
    canvas_frame_count++;
    if (canvas_frame_count % 60 == 0) {
        LOGI("ANativeWindow_unlockAndPost: 60 Canvas frames rendered");
    }
    
    int res = orig_ANativeWindow_unlockAndPost(window);
    g_in_hook = false;
    return res;
}

void my_ASurfaceTransaction_setBuffer(void* transaction, void* surfaceControl, void* buffer, int fenceFd) {
    if (g_in_hook) { orig_ASurfaceTransaction_setBuffer(transaction, surfaceControl, buffer, fenceFd); return; }
    g_in_hook = true;
    LOGI("HOOK: ASurfaceTransaction_setBuffer(tx=%p, sc=%p, buf=%p, fence=%d)", transaction, surfaceControl, buffer, fenceFd);
    g_buffer_enqueue_count++;
    orig_ASurfaceTransaction_setBuffer(transaction, surfaceControl, buffer, fenceFd);
    g_in_hook = false;
}

void my_ASurfaceTransaction_apply(void* transaction) {
    if (g_in_hook) { orig_ASurfaceTransaction_apply(transaction); return; }
    g_in_hook = true;
    LOGI("HOOK: ASurfaceTransaction_apply(tx=%p)", transaction);
    orig_ASurfaceTransaction_apply(transaction);
    g_in_hook = false;
}

void* my_ANativeWindow_fromSurface(void* env, jobject surface) {
    if (g_in_hook) return orig_ANativeWindow_fromSurface(env, surface);
    g_in_hook = true;
    
    void* win = orig_ANativeWindow_fromSurface(env, surface);
    if (g_target_window != nullptr) {
        LOGI("SPOOFING ANativeWindow_fromSurface: Redirecting %p -> %p", win, g_target_window);
        win = g_target_window;
    }
    
    g_in_hook = false;
    return win;
}

void my_ANativeWindow_acquire(void* window) {
    if (g_in_hook) { orig_ANativeWindow_acquire(window); return; }
    g_in_hook = true;
    LOGI("HOOK: ANativeWindow_acquire(%p)", window);
    orig_ANativeWindow_acquire(window);
    g_in_hook = false;
}

void my_ANativeWindow_release(void* window) {
    if (g_in_hook) { orig_ANativeWindow_release(window); return; }
    g_in_hook = true;
    LOGI("HOOK: ANativeWindow_release(%p)", window);
    orig_ANativeWindow_release(window);
    g_in_hook = false;
}

static void* (*orig_ASurfaceControl_createFromWindow)(void* parent, void* window, const char* debug_name) = nullptr;
void* my_ASurfaceControl_createFromWindow(void* parent, void* window, const char* debug_name) {
    if (g_in_hook) return orig_ASurfaceControl_createFromWindow(parent, window, debug_name);
    g_in_hook = true;
    void* win_target = window;
    if (g_target_window != nullptr) {
        LOGI("SurfaceControl Redirect: %p -> %p (Debug: %s)", window, g_target_window, debug_name ? debug_name : "null");
        win_target = g_target_window;
    }
    void* res = orig_ASurfaceControl_createFromWindow(parent, win_target, debug_name);
    g_in_hook = false;
    return res;
}

EGLSurface my_eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint *attrib_list) {
    if (g_in_hook) return orig_eglCreateWindowSurface(dpy, config, win, attrib_list);
    g_in_hook = true;
    
    EGLNativeWindowType target_win = win;
    if (g_target_window != nullptr) {
        LOGI("SPOOFING eglCreateWindowSurface: Redirecting %p -> %p", win, g_target_window);
        target_win = g_target_window;
    }

    log_step(5, "eglCreateWindowSurface START");
    LOGI("HOOK: eglCreateWindowSurface(dpy=%p, config=%p, win=%p)", dpy, config, target_win);
    EGLSurface surface = orig_eglCreateWindowSurface(dpy, config, target_win, attrib_list);
    
    if (surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface FAILED");
        check_egl_error("eglCreateWindowSurface");
    } else {
        LOGI("eglCreateWindowSurface SUCCESS: surface=%p", surface);
    }
    g_in_hook = false;
    return surface;
}

EGLBoolean my_eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    if (g_in_hook) return orig_eglMakeCurrent(dpy, draw, read, ctx);
    g_in_hook = true;
    log_step(6, "eglMakeCurrent START");
    LOGI("HOOK: eglMakeCurrent(dpy=%p, draw=%p, read=%p, ctx=%p)", dpy, draw, read, ctx);
    EGLBoolean res = orig_eglMakeCurrent(dpy, draw, read, ctx);
    if (!res) {
        LOGE("eglMakeCurrent FAILED");
        check_egl_error("eglMakeCurrent");
    } else {
        LOGI("eglMakeCurrent SUCCESS: thread_id=%d", (int)gettid());
    }
    g_in_hook = false;
    return res;
}

static void* (*orig_eglGetProcAddress)(const char* procname) = nullptr;
void* my_eglGetProcAddress(const char* procname) {
    if (g_in_hook) return orig_eglGetProcAddress(procname);
    if (procname && strcmp(procname, "eglCreateWindowSurface") == 0) {
        return (void*)my_eglCreateWindowSurface;
    }
    return orig_eglGetProcAddress(procname);
}

EGLBoolean my_eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    if (g_in_hook) return orig_eglSwapBuffers(dpy, surface);
    g_in_hook = true;
    
    g_egl_frame_count++;
    
    print_rendering_diagnosis();

    EGLBoolean res = orig_eglSwapBuffers(dpy, surface);
    if (res) {
        static bool backend_logged = false;
        if (!backend_logged) {
            LOGI(">>> RENDER BACKEND DETECTED: EGL <<<");
            backend_logged = true;
        }
    }
    if (!res) {
        LOGE("eglSwapBuffers FAILED for surface %p", surface);
        check_egl_error("eglSwapBuffers");
    }
    g_in_hook = false;
    return res;
}

// --- Vulkan Hooks ---

static PFN_vkVoidFunction my_vkGetInstanceProcAddr(VkInstance instance, const char* pName) {
    if (g_in_hook) return orig_vkGetInstanceProcAddr(instance, pName);
    if (pName && strcmp(pName, "vkCreateAndroidSurfaceKHR") == 0) {
        return (PFN_vkVoidFunction)my_vkCreateAndroidSurfaceKHR;
    }
    if (pName && strcmp(pName, "vkQueuePresentKHR") == 0) {
        return (PFN_vkVoidFunction)my_vkQueuePresentKHR;
    }
    return orig_vkGetInstanceProcAddr(instance, pName);
}

static PFN_vkVoidFunction my_vkGetDeviceProcAddr(VkDevice device, const char* pName) {
    if (g_in_hook) return orig_vkGetDeviceProcAddr(device, pName);
    if (pName && strcmp(pName, "vkQueuePresentKHR") == 0) {
        return (PFN_vkVoidFunction)my_vkQueuePresentKHR;
    }
    return orig_vkGetDeviceProcAddr(device, pName);
}

static VkResult my_vkQueuePresentKHR(VkQueue queue, const VkPresentInfoKHR* pPresentInfo) {
    if (g_in_hook) return orig_vkQueuePresentKHR(queue, pPresentInfo);
    g_in_hook = true;
    
    g_vk_frame_count++;
    
    print_rendering_diagnosis();
    
    VkResult res = orig_vkQueuePresentKHR(queue, pPresentInfo);
    if (res == VK_SUCCESS) {
        static bool vk_backend_logged = false;
        if (!vk_backend_logged) {
            LOGI(">>> RENDER BACKEND DETECTED: VULKAN <<<");
            vk_backend_logged = true;
        }
    }
    g_in_hook = false;
    return res;
}

static VkResult my_vkCreateAndroidSurfaceKHR(VkInstance instance, const VkAndroidSurfaceCreateInfoKHR* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkSurfaceKHR* pSurface) {
    if (g_in_hook) return orig_vkCreateAndroidSurfaceKHR(instance, pCreateInfo, pAllocator, pSurface);
    g_in_hook = true;
    
    LOGI("HOOK: vkCreateAndroidSurfaceKHR called");
    VkAndroidSurfaceCreateInfoKHR createInfo = *pCreateInfo;
    if (g_target_window != nullptr) {
        LOGI("VULKAN SPOOF: Redirecting window %p -> %p", createInfo.window, g_target_window);
        createInfo.window = (ANativeWindow*)g_target_window;
    }
    
    VkResult res = orig_vkCreateAndroidSurfaceKHR(instance, &createInfo, pAllocator, pSurface);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateAndroidSurfaceKHR FAILED: %d", res);
    } else {
        LOGI("vkCreateAndroidSurfaceKHR SUCCESS: surface=%p", *pSurface);
    }
    
    g_in_hook = false;
    return res;
}

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
                    fprintf(filtered, "Uid:\t%d\t%d\t%d\t%d\n", g_fake_uid, g_fake_uid, g_fake_uid, g_fake_uid);
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

// --- Runtime Hooks Logic ---
pid_t my_fork() {
    if (g_in_hook) return orig_fork();
    g_in_hook = true;
    LOGI("Blocking native fork() for child stability");
    g_in_hook = false;
    return -1;
}

long my_ptrace(int request, pid_t pid, void* addr, void* data) {
    if (g_in_hook) return orig_ptrace(request, pid, addr, data);
    g_in_hook = true;
    long res = 0;
    if (OneCore::RuntimeHelper::handlePtrace(request, pid, addr, data, &res)) {
        g_in_hook = false;
        return res;
    }
    res = orig_ptrace(request, pid, addr, data);
    g_in_hook = false;
    return res;
}

int my_system_property_get(const char* name, char* value) {
    if (g_in_hook) return orig_system_property_get(name, value);
    g_in_hook = true;
    int res = orig_system_property_get(name, value);
    OneCore::RuntimeHelper::spoofSystemProperty(name, value);
    g_in_hook = false;
    return res;
}

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
    return 2000;
}

// Updated Hook functions with guard and logic from RuntimeHook
#ifndef O_TMPFILE
#define O_TMPFILE 020200000 
#endif

static int my_open(const char *pathname, int flags, ...) {
    mode_t mode = 0;
    if (flags & (O_CREAT | O_TMPFILE)) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }
    
    if (g_in_hook) return orig_open(pathname, flags, mode);
    g_in_hook = true;
    
    if (OneCore::RuntimeHelper::isSuspiciousPath(pathname)) {
        LOGW("Blocked access to %s (anti-detect)", pathname);
        g_in_hook = false;
        errno = ENOENT;
        return -1;
    }

    std::string r_path = redirect_path(pathname);
    int res = orig_open(r_path.c_str(), flags, mode);
    g_in_hook = false;
    return res;
}

int my_openat(int dirfd, const char *pathname, int flags, ...) {
    mode_t mode = 0;
    if (flags & (O_CREAT | O_TMPFILE)) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }

    if (g_in_hook) return orig_openat(dirfd, pathname, flags, mode);
    g_in_hook = true;
    int res;
    
    if (pathname && OneCore::RuntimeHelper::isSuspiciousPath(pathname)) {
        LOGW("Blocked access to %s (anti-detect)", pathname);
        g_in_hook = false;
        errno = ENOENT;
        return -1;
    }

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
    
    if (OneCore::RuntimeHelper::isSuspiciousPath(pathname)) {
        LOGW("Blocked access(access): %s", pathname);
        g_in_hook = false;
        errno = ENOENT;
        return -1;
    }

    int res = orig_access(redirect_path(pathname).c_str(), mode);
    g_in_hook = false;
    return res;
}

int my_faccessat(int dirfd, const char *pathname, int mode, int flags) {
    if (g_in_hook) return orig_faccessat(dirfd, pathname, mode, flags);
    g_in_hook = true;
    
    if (pathname && OneCore::RuntimeHelper::isSuspiciousPath(pathname)) {
        LOGW("Blocked access(faccessat): %s", pathname);
        g_in_hook = false;
        errno = ENOENT;
        return -1;
    }

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
    
    if (OneCore::RuntimeHelper::isSuspiciousPath(pathname)) {
        LOGW("Blocked access(stat): %s", pathname);
        g_in_hook = false;
        errno = ENOENT;
        return -1;
    }

    int res = orig_stat(redirect_path(pathname).c_str(), buf);
    g_in_hook = false;
    return res;
}

int my_lstat(const char *pathname, struct stat *buf) {
    if (g_in_hook) return orig_lstat(pathname, buf);
    g_in_hook = true;
    
    if (OneCore::RuntimeHelper::isSuspiciousPath(pathname)) {
        LOGW("Blocked access(lstat): %s", pathname);
        g_in_hook = false;
        errno = ENOENT;
        return -1;
    }

    int res = orig_lstat(redirect_path(pathname).c_str(), buf);
    g_in_hook = false;
    return res;
}

int my_fstatat(int dirfd, const char *pathname, struct stat *buf, int flags) {
    if (g_in_hook) return orig_fstatat(dirfd, pathname, buf, flags);
    g_in_hook = true;
    
    if (pathname && OneCore::RuntimeHelper::isSuspiciousPath(pathname)) {
        LOGW("Blocked access(fstatat): %s", pathname);
        g_in_hook = false;
        errno = ENOENT;
        return -1;
    }

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
    
    if (OneCore::RuntimeHelper::shouldBlockDlopen(filename)) {
        LOGW("Blocked dlopen_ext for %s (anti-detect)", filename);
        g_in_hook = false;
        return nullptr;
    }

    void* res;
    const char* target_path = filename;
    std::string redirected;
    if (filename) {
        redirected = redirect_path(filename);
        target_path = redirected.c_str();
        if (redirected != filename) {
            LOGI("HOOK: dlopen_ext redirected %s -> %s", filename, target_path);
        }
    }

    res = orig_dlopen_ext(target_path, flags, extinfo);
    
    if (res && filename && strstr(filename, "libvulkan.so")) {
        LOGI("Vulkan Library (ext) Detected! Installing Vulkan hooks...");
        safe_hook(dlsym(res, "vkCreateAndroidSurfaceKHR"), (void*)my_vkCreateAndroidSurfaceKHR, (void**)&orig_vkCreateAndroidSurfaceKHR, "vkCreateAndroidSurfaceKHR");
        safe_hook(dlsym(res, "vkQueuePresentKHR"), (void*)my_vkQueuePresentKHR, (void**)&orig_vkQueuePresentKHR, "vkQueuePresentKHR");
        safe_hook(dlsym(res, "vkGetInstanceProcAddr"), (void*)my_vkGetInstanceProcAddr, (void**)&orig_vkGetInstanceProcAddr, "vkGetInstanceProcAddr");
        safe_hook(dlsym(res, "vkGetDeviceProcAddr"), (void*)my_vkGetDeviceProcAddr, (void**)&orig_vkGetDeviceProcAddr, "vkGetDeviceProcAddr");
    }

    if (!res && filename) {
        LOGE("dlopen_ext FAIL: %s (asked as %s) | error=%s", target_path, filename, dlerror());
    } else if (res && filename) {
        LOGI("dlopen_ext SUCCESS: %s", target_path);
    }

    g_in_hook = false;
    return res;
}

void* my_dlopen(const char* filename, int flags) {
    if (g_in_hook) return orig_dlopen(filename, flags);
    g_in_hook = true;
    
    if (OneCore::RuntimeHelper::shouldBlockDlopen(filename)) {
        LOGW("Blocked dlopen for %s (anti-detect)", filename);
        g_in_hook = false;
        return nullptr;
    }

    void* res;
    const char* target_path = filename;
    std::string redirected;
    if (filename) {
        redirected = redirect_path(filename);
        target_path = redirected.c_str();
        if (redirected != filename) {
            LOGI("HOOK: dlopen redirected %s -> %s", filename, target_path);
        }
    }

    res = orig_dlopen(target_path, flags);

    if (res && filename && strstr(filename, "libvulkan.so")) {
        LOGI("Vulkan Library Detected! Installing Vulkan hooks...");
        safe_hook(dlsym(res, "vkCreateAndroidSurfaceKHR"), (void*)my_vkCreateAndroidSurfaceKHR, (void**)&orig_vkCreateAndroidSurfaceKHR, "vkCreateAndroidSurfaceKHR");
        safe_hook(dlsym(res, "vkQueuePresentKHR"), (void*)my_vkQueuePresentKHR, (void**)&orig_vkQueuePresentKHR, "vkQueuePresentKHR");
        safe_hook(dlsym(res, "vkGetInstanceProcAddr"), (void*)my_vkGetInstanceProcAddr, (void**)&orig_vkGetInstanceProcAddr, "vkGetInstanceProcAddr");
        safe_hook(dlsym(res, "vkGetDeviceProcAddr"), (void*)my_vkGetDeviceProcAddr, (void**)&orig_vkGetDeviceProcAddr, "vkGetDeviceProcAddr");
    }

    if (!res && filename) {
        LOGE("dlopen FAIL: %s (asked as %s) | error=%s", target_path, filename, dlerror());
    } else if (res && filename) {
        LOGI("dlopen SUCCESS: %s", target_path);
    }

    g_in_hook = false;
    return res;
}

FILE* my_fopen(const char* filename, const char* mode) {
    if (g_in_hook) return orig_fopen(filename, mode);
    g_in_hook = true;
    
    if (OneCore::RuntimeHelper::isSuspiciousPath(filename)) {
        LOGW("Blocked access(fopen): %s", filename);
        g_in_hook = false;
        errno = ENOENT;
        return NULL;
    }

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
        LOGE("FAILED to hook: %s at %p. Reason: Dobby failure", name, target);
    }
}

static bool g_init_done = false;

static void install_all_hooks(JavaVM* vm) {
    static bool hooks_installed = false;
    if (hooks_installed) return;
    hooks_installed = true;

    LOGI("Installing All Core Hooks...");
    
    OneCore::enableStealthMode();
    JNIEnv* env = nullptr;
    if (vm && vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
         OneCore::bypassHiddenApi(env);
         OneCore::installJniHooks(env);
    }
    OneCore::installBinderHooks();

    // Hook BufferQueue (Surface)
    void* libgui = dlopen("libgui.so", RTLD_NOW);
    if (libgui) {
        LOGI("HWC CHECK: libgui found, checking for direct buffer submission...");
        // Try various mangled names for Surface::dequeueBuffer/queueBuffer
        const char* dequeue_symbols[] = {
            "_ZN7android7Surface13dequeueBufferEP19ANativeWindowBufferi",
            "_ZN7android7Surface13dequeueBufferEP22ANativeWindowBuffer_hti"
        };
        const char* queue_symbols[] = {
            "_ZN7android7Surface13queueBufferEP19ANativeWindowBufferi",
            "_ZN7android7Surface13queueBufferEP22ANativeWindowBuffer_hti"
        };

        for (int i = 0; i < 2; i++) {
            void* sym = dlsym(libgui, dequeue_symbols[i]);
            if (sym) {
                safe_hook(sym, (void*)my_Surface_dequeueBuffer, (void**)&orig_Surface_dequeueBuffer, "Surface::dequeueBuffer");
                break;
            }
        }
        for (int i = 0; i < 2; i++) {
            void* sym = dlsym(libgui, queue_symbols[i]);
            if (sym) {
                safe_hook(sym, (void*)my_Surface_queueBuffer, (void**)&orig_Surface_queueBuffer, "Surface::queueBuffer");
                break;
            }
        }
        
        // Check for HWC indicators
        if (dlsym(libgui, "_ZN7android11HwcComposerC1Ev") || dlsym(libgui, "hwc2_open")) {
             g_hwc_bypass_detected = true;
        }

        dlclose(libgui);
    }

    LOGI("Installing Early Rendering Hooks...");
    void* libandroid = dlopen("libandroid.so", RTLD_NOW);
    if (libandroid) {
        safe_hook(dlsym(libandroid, "ANativeWindow_fromSurface"), (void*)my_ANativeWindow_fromSurface, (void**)&orig_ANativeWindow_fromSurface, "ANativeWindow_fromSurface");
        safe_hook(dlsym(libandroid, "ANativeWindow_acquire"), (void*)my_ANativeWindow_acquire, (void**)&orig_ANativeWindow_acquire, "ANativeWindow_acquire");
        safe_hook(dlsym(libandroid, "ANativeWindow_release"), (void*)my_ANativeWindow_release, (void**)&orig_ANativeWindow_release, "ANativeWindow_release");
        safe_hook(dlsym(libandroid, "ANativeWindow_setBuffersGeometry"), (void*)my_ANativeWindow_setBuffersGeometry, (void**)&orig_ANativeWindow_setBuffersGeometry, "ANativeWindow_setBuffersGeometry");
        safe_hook(dlsym(libandroid, "ANativeWindow_unlockAndPost"), (void*)my_ANativeWindow_unlockAndPost, (void**)&orig_ANativeWindow_unlockAndPost, "ANativeWindow_unlockAndPost");
        safe_hook(dlsym(libandroid, "ASurfaceControl_createFromWindow"), (void*)my_ASurfaceControl_createFromWindow, (void**)&orig_ASurfaceControl_createFromWindow, "ASurfaceControl_createFromWindow");
        
        void* setBuffer = dlsym(libandroid, "ASurfaceTransaction_setBuffer");
        if (setBuffer) safe_hook(setBuffer, (void*)my_ASurfaceTransaction_setBuffer, (void**)&orig_ASurfaceTransaction_setBuffer, "ASurfaceTransaction_setBuffer");
        
        void* apply = dlsym(libandroid, "ASurfaceTransaction_apply");
        if (apply) safe_hook(apply, (void*)my_ASurfaceTransaction_apply, (void**)&orig_ASurfaceTransaction_apply, "ASurfaceTransaction_apply");

        dlclose(libandroid);
    }

    void* libegl = dlopen("libEGL.so", RTLD_NOW);
    if (libegl) {
        safe_hook(dlsym(libegl, "eglCreateWindowSurface"), (void*)my_eglCreateWindowSurface, (void**)&orig_eglCreateWindowSurface, "eglCreateWindowSurface");
        safe_hook(dlsym(libegl, "eglMakeCurrent"), (void*)my_eglMakeCurrent, (void**)&orig_eglMakeCurrent, "eglMakeCurrent");
        safe_hook(dlsym(libegl, "eglSwapBuffers"), (void*)my_eglSwapBuffers, (void**)&orig_eglSwapBuffers, "eglSwapBuffers");
        safe_hook(dlsym(libegl, "eglGetProcAddress"), (void*)my_eglGetProcAddress, (void**)&orig_eglGetProcAddress, "eglGetProcAddress");
        dlclose(libegl);
    }
    
    // Proactively check for Vulkan if already loaded
    void* libvulkan = dlopen("libvulkan.so", RTLD_NOLOAD);
    if (libvulkan) {
        LOGI("Vulkan already loaded, installing hooks...");
        safe_hook(dlsym(libvulkan, "vkCreateAndroidSurfaceKHR"), (void*)my_vkCreateAndroidSurfaceKHR, (void**)&orig_vkCreateAndroidSurfaceKHR, "vkCreateAndroidSurfaceKHR");
        safe_hook(dlsym(libvulkan, "vkQueuePresentKHR"), (void*)my_vkQueuePresentKHR, (void**)&orig_vkQueuePresentKHR, "vkQueuePresentKHR");
        safe_hook(dlsym(libvulkan, "vkGetInstanceProcAddr"), (void*)my_vkGetInstanceProcAddr, (void**)&orig_vkGetInstanceProcAddr, "vkGetInstanceProcAddr");
        safe_hook(dlsym(libvulkan, "vkGetDeviceProcAddr"), (void*)my_vkGetDeviceProcAddr, (void**)&orig_vkGetDeviceProcAddr, "vkGetDeviceProcAddr");
        dlclose(libvulkan);
    }

    // Diagnostic: Check for libhwui
    void* libhwui = dlopen("libhwui.so", RTLD_NOLOAD);
    if (libhwui) {
        LOGI("DIAG: libhwui.so is loaded. System UI rendering might be active.");
        dlclose(libhwui);
    }

    LOGI("Installing Path Redirection Hooks (LibC)");
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
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
        safe_hook(dlsym(libc, "fopen"), (void*)my_fopen, (void**)&orig_fopen, "fopen");
        safe_hook(dlsym(libc, "getppid"), (void*)my_getppid, (void**)&orig_getppid, "getppid");
        safe_hook(dlsym(libc, "fork"), (void*)my_fork, (void**)&orig_fork, "fork");
        safe_hook(dlsym(libc, "getuid"), (void*)my_getuid, (void**)&orig_getuid, "getuid");
        safe_hook(dlsym(libc, "geteuid"), (void*)my_geteuid, (void**)&orig_geteuid, "geteuid");
        safe_hook(dlsym(libc, "getgid"), (void*)my_getgid, (void**)&orig_getgid, "getgid");
        dlclose(libc);
    }

    LOGI("Installing Runtime Protection Hooks");
    OneCore::installEnvironmentProtector();
    OneCore::installDexHooks();
    OneCore::installKernelShield();
    OneCore::installSocketHooks();

    void* libdl = dlopen("libdl.so", RTLD_NOW);
    if (libdl) {
        safe_hook(dlsym(libdl, "dlopen"), (void*)my_dlopen, (void**)&orig_dlopen, "dlopen");
        void* dlopen_ext = dlsym(libdl, "android_dlopen_ext");
        if (dlopen_ext) {
            safe_hook(dlopen_ext, (void*)my_dlopen_ext, (void**)&orig_dlopen_ext, "android_dlopen_ext");
        }
        dlclose(libdl);
    }
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI(">>> OneCore Native Library LOADED by process: %d at timestamp %ld <<<", getpid(), (long)time(NULL));
    install_all_hooks(vm);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI(">>> OneCore Native Library UNLOADED from process: %d <<<", getpid());
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_NativeHookManager_initHooks(JNIEnv* env, jclass clazz, jstring virtual_root, jstring package_name) {
    install_signal_handlers();
    log_step(1, "Native Engine Initialization");
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

    LOGI(">>> Initializing OneCore Native Engine Redirection Patterns for: %s <<<", p_name);
    
    // Ensure all hooks are installed (if JNI_OnLoad failed or we are re-initializing)
    install_all_hooks(nullptr); 

    env->ReleaseStringUTFChars(virtual_root, v_root);
    env->ReleaseStringUTFChars(package_name, p_name);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_IORedirector_initNativeHooks(JNIEnv* env, jclass clazz, jstring virtual_root, jstring package_name) {
    Java_com_onecore_sdk_NativeHookManager_initHooks(env, clazz, virtual_root, package_name);
}

// --- Memory Operations ---

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

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_sdk_core_UidSpoofing_applyNative(JNIEnv* env, jclass clazz, jint fakeUid) {
    g_fake_uid = (uid_t)fakeUid;
    LOGI("Native UID Spoof value updated to: %d", fakeUid);
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
