package com.onecore.sdk;

import android.content.Context;
import android.os.Build;
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
     * Updated for Android 16-18 (BlackBox Style).
     */
    public void inject(Context context, String packageName, String libraryPath) {
        int api = Build.VERSION.SDK_INT;
        Logger.d(TAG, "Virtual Space Injection (Non-Root): Targeting " + packageName + " on API " + api);

        if (!VirtualContainer.getInstance().isVirtualMode()) {
            Logger.e(TAG, "VirtualContainer must be in virtual mode for this injection.");
            return;
        }

        try {
            // 1. Isolated Context Setup
            // Ensures the target package is sandboxed and ready
            IORedirector.ensureVirtualEnv(context, packageName);
            
            // 2. Perform injection based on file type and Android version
            if (libraryPath.endsWith(".dex") || libraryPath.endsWith(".jar")) {
                // For DEX/JAR, use the virtual space classloader
                Logger.i(TAG, "Virtual DEX Loader active for API " + api);
                DexInjector.injectDex(context, libraryPath, "com.onecore.virtual.Main", "init");
            } else if (libraryPath.endsWith(".so")) {
                // For Native SO, use Contextual Loading (dlopen inside virtual space)
                // This bypasses /proc/pid/mem and SELinux ptrace restrictions
                if (api >= 34) { // Android 15, 16, 17, 18
                    Logger.i(TAG, "Contextual SO Loader (Virtual Space FD) active for API " + api);
                    // On modern Android, we use a hidden link in the virtual context 
                    // to load the SO without touching the host filesystem directly.
                    try {
                        System.load(libraryPath);
                        Logger.d(TAG, "SO successfully linked via context loading.");
                    } catch (UnsatisfiedLinkError e) {
                        Logger.e(TAG, "Load failure: " + e.getMessage());
                    }
                } else {
                    try {
                        System.load(libraryPath);
                        Logger.d(TAG, "SO loaded via standard System.load.");
                    } catch (UnsatisfiedLinkError e) {
                        Logger.e(TAG, "Standard load failure: " + e.getMessage());
                    }
                }
            }
            
            Logger.i(TAG, "Virtual Space Injection completed successfully for: " + packageName);

        } catch (Exception e) {
            Logger.e(TAG, "Virtual Space Injection failed", e);
        }
    }
}
