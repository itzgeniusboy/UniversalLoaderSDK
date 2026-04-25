package com.onecore.loader;

import android.content.Context;
import android.util.Log;

/**
 * Android 15 Switcher.
 * Simplified for minimal working version.
 */
public class Android15Handler {
    private static final String TAG = "OneCore-A15";

    public static void prepareEnvironment(Context context) {
        Log.i(TAG, "Environment preparation skipped in minimal version.");
    }
}
