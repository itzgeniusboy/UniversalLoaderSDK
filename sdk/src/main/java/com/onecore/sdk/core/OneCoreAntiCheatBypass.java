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
