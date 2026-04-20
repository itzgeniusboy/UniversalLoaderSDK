package com.onecore.loader;

import android.content.Context;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * High-level Library Manager.
 * Solves: Context Mismatch by queuing injection for the Sandbox Process.
 */
public class LibraryInjector {
    private static final String TAG = "LibraryInjector";

    public static void inject(Context context, String packageName, String libPath) {
        if (libPath == null || !new File(libPath).exists()) {
            Logger.e(TAG, "Library not found: " + libPath);
            return;
        }

        Logger.i(TAG, "Preparing Library for Sandbox Injection: " + libPath);
        
        // CRITICAL FIX: Do not inject in Loader process. 
        // Pass to VirtualContainer to deliver to SandboxActivity via Intent.
        VirtualContainer.getInstance().injectToVirtualSpace(context, packageName, libPath);
    }
}
