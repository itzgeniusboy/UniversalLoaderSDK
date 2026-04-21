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

    public static void inject(Context context, String packageName, int pid) {
        File libFile = new File(context.getFilesDir(), LIB_NAME);
        if (!libFile.exists()) {
            Logger.e("Injector", "Library not found at: " + libFile.getAbsolutePath());
            return;
        }

        Logger.i("Injector", "Injecting into PID " + pid + " for package " + packageName);
        
        // Use NativeInjector via VirtualContainer to perform ptrace injection into the running PID
        VirtualContainer.getInstance().injectLibrary(context, packageName, libFile.getAbsolutePath());
    }
}
