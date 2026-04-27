package com.onecore.sdk;

import com.onecore.sdk.utils.Logger;

/**
 * JNI Wrapper for Native Hooking and Memory Operations in OneCore SDK Engine.
 */
public class NativeHook {
    private static final String TAG = "NativeHook";
    private static boolean isLoaded = false;

    static {
        try {
            System.loadLibrary("onecore_native");
            isLoaded = true;
            Logger.d(TAG, "Native library loaded successfully.");
        } catch (UnsatisfiedLinkError e) {
            Logger.e(TAG, "Failed to load native library: " + e.getMessage());
        }
    }

    public static boolean isAvailable() {
        return isLoaded;
    }

    /**
     * Hooks a native function.
     * @param targetAddr Address of the function to hook.
     * @param replaceAddr Address of the replacement function.
     * @return Address of the original function (trampoline).
     */
    public native long hookFunction(long targetAddr, long replaceAddr);

    /**
     * Reads memory from the current process.
     */
    public native byte[] readMemoryNative(long addr, int size);

    /**
     * Writes memory to the current process.
     */
    public native boolean writeMemoryNative(long addr, byte[] data);

    /**
     * Advanced: Reads memory from another process.
     */
    public native boolean readProcessMemory(int pid, long addr, byte[] buffer);

    /**
     * Advanced: Writes memory to another process.
     */
    public native boolean writeProcessMemory(int pid, long addr, byte[] buffer);

    /**
     * Installs low-level Binder transaction hooks.
     */
    public native void installBinderHook();
}
