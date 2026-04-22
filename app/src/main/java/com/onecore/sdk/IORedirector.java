package com.onecore.sdk;

import java.io.File;
import android.content.Context;
import android.os.Build;
import com.onecore.sdk.utils.Logger;

/**
 * Android 14+ IO Redirection Bridge.
 * Connects Java-level logic to Native Syscall Hooks.
 */
public class IORedirector {
    private static final String TAG = "OneCore-IO";

    static {
        try {
            System.loadLibrary("onecore");
            Logger.i(TAG, "Native IO Hook Engine Linked.");
        } catch (UnsatisfiedLinkError e) {
            Logger.e(TAG, "Failed to load native IO library: " + e.getMessage());
        }
    }

    /**
     * Initializes the native redirection hooks.
     * @param virtualRoot The root path for virtual storage.
     */
    public static native void initNativeHooks(String virtualRoot, String packageName);

    public static String redirectPath(String originalPath, String packageName) {
        if (originalPath == null) return null;

        String virtualRoot = "/data/data/com.onecore.loader/files/virtual/" + packageName;

        // Simple Java fallback for display names or logging
        if (originalPath.startsWith("/data/data/" + packageName)) {
            return originalPath.replace("/data/data/" + packageName, virtualRoot + "/data");
        }

        return originalPath;
    }

    public static void ensureVirtualEnv(Context context, String packageName) {
        File virtualDir = new File(context.getFilesDir(), "virtual/" + packageName);
        if (!virtualDir.exists()) virtualDir.mkdirs();
        
        new File(virtualDir, "data/files").mkdirs();
        new File(virtualDir, "data/cache").mkdirs();
        
        String virtualRoot = virtualDir.getAbsolutePath();
        
        // 1. Initialize Native Hooks with the calculated paths
        initNativeHooks(virtualRoot, packageName);
        
        Logger.i(TAG, "Virtual IO Environment Initialized for: " + packageName);
    }

    /**
     * Stub for future FUSE passthrough support.
     */
    public static void setupFusePassthrough(Context context) {
        // Android 17-18 specific logic would go here
        if (Build.VERSION.SDK_INT >= 31) {
            Logger.d(TAG, "FUSE Passthrough verification triggered (No-op on API < 37)");
        }
    }
}
