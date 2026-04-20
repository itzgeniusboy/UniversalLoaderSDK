package com.onecore.loader;

import android.content.Context;
import android.os.Build;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Modern Library Injector for OneCore SDK (BlackBox style).
 * Supports non-root injection into virtual space on Android 8-18 (API 26-38).
 */
public class LibraryInjector {
    private static final String TAG = "LibraryInjector";

    public static void inject(Context context, String packageName, String libPath) {
        if (libPath == null || !new File(libPath).exists()) {
            Logger.e(TAG, "Injection failed: Library file not found at " + libPath);
            return;
        }

        int api = Build.VERSION.SDK_INT;
        Logger.i(TAG, "BlackBox Injection starting for API " + api + " : " + packageName);

        try {
            // Check for Android 16-18 (future-proofing)
            if (api >= 36) {
                injectForFutureAndroid(context, packageName, libPath);
            } else if (api >= 34) {
                injectForModernAndroid(context, packageName, libPath);
            } else {
                injectLegacy(context, packageName, libPath);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Critical error during BlackBox injection", e);
        }
    }

    private static void injectForFutureAndroid(Context context, String packageName, String libPath) {
        Logger.d(TAG, "Applying Android 16-18 Sandbox Injection...");
        // On future Android, we must ensure the library is loaded via the virtual space's 
        // ownClassLoader to bypass stricter /proc/pid/mem controls.
        VirtualContainer.getInstance().injectToVirtualSpace(context, packageName, libPath);
    }

    private static void injectForModernAndroid(Context context, String packageName, String libPath) {
        Logger.d(TAG, "Applying Android 14/15 Virtual Space Injection...");
        VirtualContainer.getInstance().injectToVirtualSpace(context, packageName, libPath);
    }

    private static void injectLegacy(Context context, String packageName, String libPath) {
        Logger.d(TAG, "Applying Legacy Virtual Space Injection...");
        VirtualContainer.getInstance().injectLibrary(context, packageName, libPath);
    }
}
