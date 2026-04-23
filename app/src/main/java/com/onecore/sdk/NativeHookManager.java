package com.onecore.sdk;

import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Manages the connection between the Java Virtual Container and Native Syscall Hooks.
 */
public class NativeHookManager {
    private static final String TAG = "NativeHookManager";

    static {
        try {
            System.loadLibrary("onecore_native");
        } catch (UnsatisfiedLinkError e) {
            Logger.e(TAG, "Failed to load native hooks library");
        }
    }

    /**
     * Initializes native syscall hooks for path redirection and sandbox isolation.
     * @param context Application context
     * @param virtualRoot The root directory for sandbox data
     * @param packageName The guest package name
     */
    public static void setupIsolation(Context context, String virtualRoot, String packageName) {
        try {
            File root = new File(virtualRoot);
            if (!root.exists()) root.mkdirs();
            
            initHooks(virtualRoot, packageName);
            Logger.i(TAG, "Native Isolation Layers ACTIVE for " + packageName);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to setup native isolation", e);
        }
    }

    private static native void initHooks(String virtualRoot, String packageName);
}
