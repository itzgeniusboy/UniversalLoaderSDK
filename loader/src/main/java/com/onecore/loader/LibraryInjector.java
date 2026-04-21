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
    private static final String LIB_NAME = "libbgmi.so";

    public static void inject(Context context, String packageName, String customPath) {
        String finalPath;
        if (customPath != null) {
            finalPath = customPath;
        } else {
            // Default: Look in extracted_libs directory
            File extractedLib = new File(context.getFilesDir(), "extracted_libs/" + LIB_NAME);
            if (!extractedLib.exists()) {
                // Secondary check: maybe it's in the root files dir
                extractedLib = new File(context.getFilesDir(), LIB_NAME);
            }
            finalPath = extractedLib.getAbsolutePath();
        }

        File libFile = new File(finalPath);
        if (!libFile.exists()) {
            Logger.e("Injector", "CRITICAL ERROR: " + LIB_NAME + " not found after extraction!");
            return;
        }

        Logger.i("Injector", "Virtual Space Link: [Target: " + packageName + "] -> " + LIB_NAME);
        
        try {
            // Same process injection - no PID needed
            VirtualContainer.getInstance().injectLibrary(context, packageName, libFile.getAbsolutePath());
            Logger.d("Injector", "Injection Success: " + LIB_NAME);
        } catch (Exception e) {
            Logger.e("Injector", "Failed to perform local injection", e);
        }
    }
}
