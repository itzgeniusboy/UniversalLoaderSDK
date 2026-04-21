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
    private static final String LIB_NAME = "modded_esp.so";

    public static void inject(Context context, String packageName, int pid, String libPath) {
        String finalPath = (libPath != null) ? libPath : new File(context.getFilesDir(), LIB_NAME).getAbsolutePath();
        File libFile = new File(finalPath);
        if (!libFile.exists()) {
            Logger.e("Injector", "Library not found at: " + libFile.getAbsolutePath());
            return;
        }

        Logger.i("Injector", "Linking SO to PID: " + pid + " [Target: " + packageName + "]");
        
        try {
            // Use NativeInjector via VirtualContainer to perform ptrace injection into the running PID
            VirtualContainer.getInstance().injectLibrary(context, packageName, libFile.getAbsolutePath());
            Logger.d("Injector", "Linker Command Dispatched Success.");
        } catch (Exception e) {
            Logger.e("Injector", "Failed to dispatch linker command", e);
        }
    }
}
