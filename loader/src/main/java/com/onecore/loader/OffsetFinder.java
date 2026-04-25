package com.onecore.loader;

import android.content.Context;
import android.util.Log;

/**
 * Dynamic Offset Finder.
 * Simplified for minimal working version.
 */
public class OffsetFinder {
    private static final String TAG = "OffsetFinder";

    public static long findGWorld(Context context) {
        Log.i(TAG, "Offset finding skipped in minimal version.");
        return 0;
    }
}
