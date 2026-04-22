package com.onecore.io;

import android.content.Context;
import com.onecore.sdk.utils.Logger;

/**
 * Handles IO Redirection between the Guest and the Virtual Host.
 * Maps guest data paths to managed sandbox subdirectories.
 */
public class IORedirector {
    private static final String TAG = "IORedirector";

    // Native method to initialize syscall hooks
    public static native void initNative(String virtualRoot, String packageName);

    /**
     * Prepares the virtual environment directories and triggers native hooks.
     */
    public static void setup(Context context, String packageName) {
        String virtualRoot = context.getFilesDir().getAbsolutePath() + "/virtual/" + packageName;
        
        java.io.File dataDir = new java.io.File(virtualRoot, "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        
        Logger.i(TAG, "Setting up IO Redirection for " + packageName + " at " + virtualRoot);
        
        try {
            initNative(virtualRoot, packageName);
        } catch (UnsatisfiedLinkError e) {
            Logger.e(TAG, "Failed to initialize native hooks: " + e.getMessage());
        }
    }
}
