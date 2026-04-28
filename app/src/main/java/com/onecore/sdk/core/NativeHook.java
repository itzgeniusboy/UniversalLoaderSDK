package com.onecore.sdk.core;

import com.onecore.sdk.utils.Logger;

/**
 * Interface to the native hooking engine.
 */
public class NativeHook {
    private static final String TAG = "NativeHook";

    public static boolean isAvailable() {
        try {
            System.loadLibrary("onecore_native");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public void installBinderHook() {
        Logger.i(TAG, "Installing Native Binder Hooks...");
        try {
            nativeInstallBinderHook();
        } catch (UnsatisfiedLinkError e) {
            Logger.e(TAG, "Native binder hook failed - library symbols missing.");
        }
    }

    public long hookFunction(long targetAddr, long replaceAddr) {
        return nativeHookFunction(targetAddr, replaceAddr);
    }

    private native void nativeInstallBinderHook();
    private native long nativeHookFunction(long targetAddr, long replaceAddr);
}
