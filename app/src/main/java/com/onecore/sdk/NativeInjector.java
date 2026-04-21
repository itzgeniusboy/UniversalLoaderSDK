package com.onecore.sdk;

import com.onecore.sdk.utils.Logger;

/**
 * Native Library (.so) Injection Wrapper for OneCore SDK Engine.
 * Interfaces with the native ptrace-based injector.
 */
public class NativeInjector {
    private static final String TAG = "NativeInjector";

    static {
        try {
            System.loadLibrary("onecore_injector");
        } catch (UnsatisfiedLinkError e) {
            Logger.e(TAG, "Native injector library not loaded.");
        }
    }

    /**
     * Injects a shared library into a target process via ptrace.
     * @param pid Target process ID.
     * @param libraryPath Full path to the .so file.
     * @return 0 on success, negative error code otherwise.
     */
    public static native int injectSo(int pid, String libraryPath);

    /**
     * Standalone method to perform injection.
     */
    public static void performInjection(int pid, String soPath) {
        if (pid <= 0) {
            Logger.e(TAG, "Invalid PID for injection: " + pid);
            return;
        }
        
        File lib = new File(soPath);
        if (!lib.exists()) {
            Logger.e(TAG, "Library not found at: " + soPath);
            return;
        }

        Logger.i(TAG, "Initiating remote injection: PID=" + pid + ", Path=" + soPath);
        int result = injectSo(pid, soPath);
        
        if (result == 0) {
            Logger.i(TAG, "Injection sequence completed successfully for PID " + pid);
        } else {
            Logger.e(TAG, "Injection sequence failed with error code: " + result);
        }
    }
}
