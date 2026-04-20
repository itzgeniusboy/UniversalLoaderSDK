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

    public static void performInjection(Context context, String packageName, String libPath) {
        int api = Build.VERSION.SDK_INT;
        Logger.i(TAG, "Targeting API " + api + " for " + packageName);

        try {
            if (api >= 34) { // Android 15, 16, 17, 18
                // Use Modern Virtualization Loading (BlackBox style)
                // This loads the SO before the entry point of the guest activity
                VirtualContainer.getInstance().injectToVirtualSpace(context, packageName, libPath);
            } else if (api >= 30) { // Android 11-13
                // Use Namespace bridging for Scoped Storage bypass
                VirtualContainer.getInstance().injectLibrary(context, packageName, libPath);
            } else { // Android 8-10
                // Standard Loader with Legacy Path support
                VirtualContainer.getInstance().injectLibrary(context, packageName, libPath);
            }
            Logger.d(TAG, "Injection sequence completed successfully.");
        } catch (Exception e) {
            Logger.e(TAG, "Critical Injection Failure", e);
        }
    }
}
