package com.onecore.io;

import android.content.Context;
import android.util.Log;

/**
 * Handles IO Redirection.
 * Simplified for minimal working version.
 */
public class IORedirector {
    private static final String TAG = "IORedirector";

    public static void startRedirection(Context context, String packageName) {
        Log.i(TAG, "Starting SDK IO Redirection Engine...");
        com.onecore.sdk.IORedirector.ensureVirtualEnv(context, packageName);
    }
}
