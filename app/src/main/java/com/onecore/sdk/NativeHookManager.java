package com.onecore.sdk;

import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Early-Stage Native Hooking Manager.
 * Solves: Engineinitfailure by installing syscall hooks before Guest Load.
 */
public class NativeHookManager {
    private static final String TAG = "NativeHookManager";
    private static boolean hooksInstalled = false;

    static {
        try {
            System.loadLibrary("onecore_native");
        } catch (UnsatisfiedLinkError e) {
            Logger.e(TAG, "Native library 'onecore_native' missing!");
        }
    }

    /**
     * Installs path redirection hooks.
     * MUST be called at the very beginning of the sandbox process.
     */
    public static synchronized void setupIsolation(Context context, String virtualRoot, String packageName) {
        if (hooksInstalled) return;

        try {
            File root = new File(virtualRoot);
            if (!root.exists()) root.mkdirs();
            
            // Create subdirectories for redirection
            new File(root, "data").mkdirs();
            new File(root, "obb").mkdirs();
            new File(root, "external").mkdirs();

            initHooks(virtualRoot, packageName);
            
            // Initialize level-2 Binder Hooks
            new NativeHook().installBinderHook();
            
            hooksInstalled = true;
            Logger.i(TAG, "Virtual Syscall Layer: OPERATIONAL");
        } catch (Exception e) {
            Logger.e(TAG, "Failed to initialize native isolation layer", e);
        }
    }

    private static native void initHooks(String virtualRoot, String packageName);
    public static native void setTargetSurface(android.view.Surface surface);
}
