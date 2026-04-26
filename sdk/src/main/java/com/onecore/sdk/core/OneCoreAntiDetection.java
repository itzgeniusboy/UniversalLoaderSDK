package com.onecore.sdk.core;

import android.util.Log;
import java.io.File;

/**
 * Provides a basic layer to hide virtualization markers from the guest application.
 */
public class OneCoreAntiDetection {
    private static final String TAG = "OneCore-AntiDetect";

    public static void apply() {
        Log.i(TAG, "OneCore-DEBUG: Applying Anti-Detection Layer...");
        
        // 1. Trigger the Anti-Cheat bypass as part of anti-detection
        OneCoreAntiCheatBypass.apply();

        // 2. Mocking specific paths in the file system for Java-side checks
        // This is handled by our IORedirector but we adds a hint here
        
        // 3. Fake SELinux state: Games check this often
        System.setProperty("persist.sys.selinux", "enforcing");
        
        // 4. Force hide 'onecore' from system properties if any leaked
        System.setProperty("onecore.version", ""); 

        Log.d(TAG, "Anti-Detection layer active and hardened.");
    }

    /**
     * Checks if a specific system property should be masked.
     */
    public static boolean shouldMaskProperty(String key) {
        return key.contains("onecore") || key.contains("loader") || key.contains("v_");
    }
}
