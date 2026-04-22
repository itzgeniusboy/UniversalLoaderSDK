package com.onecore.io;

import java.io.File;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * Android 14 IO Redirection Bridge.
 * Dedicated to redirecting BGMI 4.3.0 file paths to virtual sandbox.
 */
public class IORedirector {
    private static final String TAG = "OneCore-IO";

    static {
        try {
            System.loadLibrary("onecore");
            Log.i(TAG, "Native IO Engine (onecore) loaded successfully.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library 'onecore' missing. Hooks will not be applied.");
        }
    }

    /**
     * Native hook initialization for syscall interception.
     */
    public static native void initHooks(String virtualPath, String targetPackage);

    /**
     * Prepares the sandbox directory and triggers native hooks.
     */
    public static void startRedirection(Context context, String packageName) {
        File virtualDir = new File(context.getFilesDir(), "virtual/" + packageName);
        if (!virtualDir.exists()) {
            virtualDir.mkdirs();
        }
        
        // Prepare data structure
        new File(virtualDir, "data").mkdirs();
        new File(virtualDir, "obb").mkdirs();
        
        String virtualPath = virtualDir.getAbsolutePath();
        Log.i(TAG, "Starting native redirection at: " + virtualPath);
        
        // Connect to Native C++ Engine
        try {
            initHooks(virtualPath, packageName);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method initHooks not found. Ensure CMake is configured correctly.");
        }
    }
}
