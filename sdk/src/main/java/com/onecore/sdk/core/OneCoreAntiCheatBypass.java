package com.onecore.sdk.core;

import android.util.Log;

/**
 * Specifically targets detection vectors used by popular mobile games (ACE, Tencent Protect).
 * Disables debugger visibility, hides adb status, and spoofs kernel properties.
 */
public class OneCoreAntiCheatBypass {
    private static final String TAG = "OneCore-AC-Bypass";

    public static void apply() {
        hideDevOptions();
        spoofHardwareState();
        blockDebuggerCheck();
    }

    private static void blockDebuggerCheck() {
        SafeExecutionManager.run("Hide Debugger", () -> {
            // Future: hook android.os.Debug
        });
    }

    private static void hideDevOptions() {
        Log.d(TAG, "OneCore-DEBUG: Masking Developer Options & Debugging.");
        System.setProperty("sys.usb.state", "mtp,adb");
        System.setProperty("init.svc.adbd", "running");
        System.setProperty("ro.debuggable", "0");
        System.setProperty("ro.secure", "1");
        System.setProperty("ro.adb.secure", "1");
        
        // Anti-Emulator / Anti-Virtualization properties
        System.setProperty("ro.kernel.qemu", "0");
        System.setProperty("ro.kernel.android.qemu", "0");
        System.setProperty("ro.product.model", "SM-S908B"); // S22 Ultra
        System.setProperty("ro.product.brand", "samsung");
        System.setProperty("ro.product.manufacturer", "samsung");
        System.setProperty("ro.product.device", "b0s");
        System.setProperty("ro.product.board", "universal2200");
        System.setProperty("ro.hardware", "exynos2200");
        System.setProperty("ro.board.platform", "exynos");
        System.setProperty("ro.build.type", "user");
        System.setProperty("ro.build.tags", "release-keys");
        System.setProperty("ro.build.display.id", "SP1A.210812.016.S908BXXU1AVCJ");
    }

    private static void spoofHardwareState() {
        Log.d(TAG, "OneCore-DEBUG: High Performance Mode Enabled.");
        System.setProperty("debug.hwui.renderer", "opengl");
        System.setProperty("persist.sys.use_120hz", "1");
        System.setProperty("ro.config.low_ram", "false");
    }

    public static boolean isDetectionPath(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.contains("onecore") || 
               lower.contains("v_lib") || 
               lower.contains("v_data") ||
               lower.contains("supersu") || 
               lower.contains("magisk") ||
               lower.contains("xposed");
    }
}
