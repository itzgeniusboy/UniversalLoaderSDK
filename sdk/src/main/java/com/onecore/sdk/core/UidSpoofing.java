package com.onecore.sdk.core;

import android.util.Log;

/**
 * Connects to native UID spoofing.
 */
public class UidSpoofing {
    private static final String TAG = "OneCore-UidSpoofing";

    static {
        try {
            System.loadLibrary("onecore_native");
        } catch (Throwable ignored) {}
    }

    public static native void applyNative(int fakeUid);

    public static void apply(int uid) {
        try {
            applyNative(uid);
            Log.i(TAG, "Native UID spoofed to: " + uid);
        } catch (Throwable t) {
            Log.w(TAG, "Native UID spoof failed: " + t.getMessage());
        }
    }
}
