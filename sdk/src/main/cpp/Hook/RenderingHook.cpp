#include "RenderingHook.h"
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <EGL/egl.h>
#include "../dobby.h"
#include "../Utils/RecursionGuard.h"

#define TAG "OneCore-Rendering"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Function pointers for original implementations
typedef ANativeWindow* (*ANativeWindow_fromSurface_t)(JNIEnv* env, jobject surface);
typedef void (*ANativeWindow_acquire_t)(ANativeWindow* window);
typedef void (*ANativeWindow_release_t)(ANativeWindow* window);

typedef EGLSurface (*eglCreateWindowSurface_t)(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint *attrib_list);
typedef EGLBoolean (*eglMakeCurrent_t)(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
typedef EGLBoolean (*eglSwapBuffers_t)(EGLDisplay dpy, EGLSurface surface);

static ANativeWindow_fromSurface_t orig_ANativeWindow_fromSurface = nullptr;
static ANativeWindow_acquire_t orig_ANativeWindow_acquire = nullptr;
static ANativeWindow_release_t orig_ANativeWindow_release = nullptr;

static eglCreateWindowSurface_t orig_eglCreateWindowSurface = nullptr;
static eglMakeCurrent_t orig_eglMakeCurrent = nullptr;
static eglSwapBuffers_t orig_eglSwapBuffers = nullptr;

// Hook implementations
ANativeWindow* my_ANativeWindow_fromSurface(JNIEnv* env, jobject surface) {
    if (g_in_hook) return orig_ANativeWindow_fromSurface(env, surface);
    g_in_hook = true;

    LOGD("[RenderingHook] ANativeWindow_fromSurface called");

    ANativeWindow* window = orig_ANativeWindow_fromSurface(env, surface);

    if (window == nullptr) {
        LOGE("[RenderingHook] Window is NULL — no fallback used");
        g_in_hook = false;
        return nullptr;
    }

    LOGD("[RenderingHook] Valid window acquired: %p", window);

    g_in_hook = false;
    return window;
}

void my_ANativeWindow_acquire(ANativeWindow* window) {
    if (g_in_hook) {
        orig_ANativeWindow_acquire(window);
        return;
    }
    g_in_hook = true;

    LOGI("HOOK: ANativeWindow_acquire(window=%p)", window);
    orig_ANativeWindow_acquire(window);

    g_in_hook = false;
}

void my_ANativeWindow_release(ANativeWindow* window) {
    if (g_in_hook) {
        orig_ANativeWindow_release(window);
        return;
    }
    g_in_hook = true;

    LOGI("HOOK: ANativeWindow_release(window=%p)", window);
    orig_ANativeWindow_release(window);

    g_in_hook = false;
}

// EGL Hooks
EGLSurface my_eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint *attrib_list) {
    if (g_in_hook) return orig_eglCreateWindowSurface(dpy, config, win, attrib_list);
    g_in_hook = true;

    LOGI("HOOK: eglCreateWindowSurface(dpy=%p, config=%p, win=%p, attrib_list=%p)", dpy, config, (void*)win, attrib_list);
    
    EGLSurface surface = orig_eglCreateWindowSurface(dpy, config, win, attrib_list);
    
    if (surface == EGL_NO_SURFACE) {
        LOGE("FAILURE: eglCreateWindowSurface returned EGL_NO_SURFACE. Window=%p, Error=0x%x", (void*)win, eglGetError());
    } else {
        LOGI("SUCCESS: Created EGLSurface %p for window %p", surface, (void*)win);
    }

    g_in_hook = false;
    return surface;
}

EGLBoolean my_eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    if (g_in_hook) return orig_eglMakeCurrent(dpy, draw, read, ctx);
    g_in_hook = true;

    LOGI("HOOK: eglMakeCurrent(dpy=%p, draw=%p, read=%p, ctx=%p)", dpy, draw, read, ctx);
    
    if (draw == EGL_NO_SURFACE || read == EGL_NO_SURFACE) {
        LOGE("WARNING: eglMakeCurrent called with EGL_NO_SURFACE");
    }

    EGLBoolean result = orig_eglMakeCurrent(dpy, draw, read, ctx);
    
    if (result == EGL_FALSE) {
        LOGE("FAILURE: eglMakeCurrent FAILED. Error=0x%x", eglGetError());
    }

    g_in_hook = false;
    return result;
}

EGLBoolean my_eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    if (g_in_hook) return orig_eglSwapBuffers(dpy, surface);
    g_in_hook = true;

    // Log every 60 frames to avoid log spam
    static int frame_count = 0;
    if (++frame_count % 60 == 0) {
        LOGI("HOOK: eglSwapBuffers(dpy=%p, surface=%p) - 60 frames rendered", dpy, surface);
    }

    EGLBoolean result = orig_eglSwapBuffers(dpy, surface);
    
    if (result == EGL_FALSE) {
        LOGE("FAILURE: eglSwapBuffers FAILED. Error=0x%x", eglGetError());
    }

    g_in_hook = false;
    return result;
}

namespace OneCore {
    void installRenderingHooks() {
        LOGI("Installing Rendering Hooks (NativeWindow + EGL Pipeline)...");

        void* libandroid = dlopen("libandroid.so", RTLD_NOW);
        if (libandroid) {
            DobbyHook(dlsym(libandroid, "ANativeWindow_fromSurface"), 
                      (void*)my_ANativeWindow_fromSurface, 
                      (void**)&orig_ANativeWindow_fromSurface);
            
            DobbyHook(dlsym(libandroid, "ANativeWindow_acquire"), 
                      (void*)my_ANativeWindow_acquire, 
                      (void**)&orig_ANativeWindow_acquire);
            
            DobbyHook(dlsym(libandroid, "ANativeWindow_release"), 
                      (void*)my_ANativeWindow_release, 
                      (void**)&orig_ANativeWindow_release);
            
            LOGI("NativeWindow Hooks INSTALLED.");
            dlclose(libandroid);
        }

        void* libegl = dlopen("libEGL.so", RTLD_NOW);
        if (libegl) {
            DobbyHook(dlsym(libegl, "eglCreateWindowSurface"),
                      (void*)my_eglCreateWindowSurface,
                      (void**)&orig_eglCreateWindowSurface);
            
            DobbyHook(dlsym(libegl, "eglMakeCurrent"),
                      (void*)my_eglMakeCurrent,
                      (void**)&orig_eglMakeCurrent);

            DobbyHook(dlsym(libegl, "eglSwapBuffers"),
                      (void*)my_eglSwapBuffers,
                      (void**)&orig_eglSwapBuffers);
            
            LOGI("EGL Hooks INSTALLED.");
            dlclose(libegl);
        }

        LOGI("Rendering Hooks Installation Cycle COMPLETE.");
    }
}
