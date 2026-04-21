package com.onecore.loader;

import android.content.Context;
import android.os.Build;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;

/**
 * Version-aware library manager for Android 8 through 18.
 * Handles the selection of the best injection strategy based on the host OS.
 */
public class UniversalInjector {
    private static final String TAG = "UniversalInjector";

    public static void performInjection(Context context, String packageName, int pid, String libPath) {
        // Delegate to the modern BlackBox-style LibraryInjector
        LibraryInjector.inject(context, packageName, pid, libPath);
    }
}
