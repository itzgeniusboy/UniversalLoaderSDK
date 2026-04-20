package com.onecore.loader;

import android.content.Context;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Optimized Injector for modded libraries with built-in offsets.
 * Simply queues the library for delivery to the Sandbox Context.
 */
public class LibraryInjector {
    private static final String TAG = "LibraryInjector";

    public static void inject(Context context, String packageName, String libPath) {
        if (libPath == null || !new File(libPath).exists()) {
            Logger.e(TAG, "Library file missing at: " + libPath);
            return;
        }

        Logger.i(TAG, "Injecting modded library (ESP/Aimbot) into " + packageName);
        
        // Pass to VirtualContainer for Sandbox Process Delivery
        VirtualContainer.getInstance().injectToVirtualSpace(context, packageName, libPath);
    }
}
