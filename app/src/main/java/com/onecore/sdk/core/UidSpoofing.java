package com.onecore.sdk.core;

import com.onecore.sdk.utils.Logger;

/**
 * Android 14+ UID Spoofing for Non-Root Sandbox.
 * Fakes the process UID to prevent identity detection by guest apps.
 */
public class UidSpoofing {
    private static final String TAG = "OneCore-UidSpoof";

    public static void apply(android.content.Context context, int fakeUid) {
        try {
            Logger.i(TAG, "Applying UID Spoof: " + fakeUid);
            applyNative(fakeUid);
            Logger.d(TAG, "Native UID Hook Active.");
        } catch (Exception e) {
            Logger.e(TAG, "UID Spoofing failed", e);
        }
    }

    private static native void applyNative(int fakeUid);
}
