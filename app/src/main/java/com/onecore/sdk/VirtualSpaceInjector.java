package com.onecore.sdk;

import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Virtual Space Injection (LSPatch style) for OneCore SDK Engine.
 * Injects libraries into a virtual space without modifying the target APK.
 */
public class VirtualSpaceInjector {
    private static final String TAG = "VirtualSpaceInjector";

    /**
     * Injects a library into a package's virtual space.
     */
    public void inject(Context context, String packageName, String libraryPath) {
        Logger.d(TAG, "Virtual Space Injection: Targeting " + packageName);

        if (!VirtualContainer.getInstance().isVirtualMode()) {
            Logger.e(TAG, "VirtualContainer must be in virtual mode for this injection.");
            return;
        }

        try {
            // 1. Ensure the virtual container knows about this package
            IORedirector.ensureVirtualEnv(context, packageName);
            
            // 2. Perform the injection based on file type
            if (libraryPath.endsWith(".dex") || libraryPath.endsWith(".jar")) {
                DexInjector.injectDex(context, libraryPath, "com.onecore.virtual.Main", "init");
            } else if (libraryPath.endsWith(".so")) {
                NativeInjector.performInjection(0, libraryPath);
            }
            
            Logger.i(TAG, "Virtual Space Injection completed for: " + packageName);

        } catch (Exception e) {
            Logger.e(TAG, "Virtual Space Injection failed", e);
        }
    }
}
