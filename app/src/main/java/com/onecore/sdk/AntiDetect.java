package com.onecore.sdk;

import android.os.Build;
import android.os.Debug;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Handles security checks such as root detection, emulator detection,
 * and debugging status.
 */
public class AntiDetect {
    private static final String TAG = "AntiDetect";
    private static AntiDetect instance;

    private AntiDetect() {}

    public static synchronized AntiDetect getInstance() {
        if (instance == null) {
            instance = new AntiDetect();
        }
        return instance;
    }

    public boolean isDebuggerAttached() {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        boolean attached = Debug.isDebuggerConnected() || Debug.waitingForDebugger();
        if (attached) Logger.w(TAG, "Debugger detected!");
        return attached;
    }

    public boolean isEmulator() {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        boolean isEmu = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
        
        if (isEmu) Logger.w(TAG, "Emulator detected!");
        return isEmu;
    }

    public boolean isRooted() {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        
        // Root Detection Bypass: If running in virtual environment, always report non-root
        if (VirtualContainer.getInstance().isVirtualMode()) {
            Logger.d(TAG, "Virtual Environment detected: Internal Root Bypass active.");
            return false;
        }

        String[] paths = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) {
                Logger.w(TAG, "Root detected at: " + path);
                return true;
            }
        }
        return false;
    }

    public boolean isTampered() {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        // Basic check for unconventional build tags
        String buildTags = Build.TAGS;
        boolean tampered = buildTags != null && buildTags.contains("test-keys");
        if (tampered) Logger.w(TAG, "Build tampering detected (test-keys)!");
        return tampered;
    }
}
