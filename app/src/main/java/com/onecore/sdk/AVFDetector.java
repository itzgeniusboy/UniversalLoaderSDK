package com.onecore.sdk;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * Android 15 Android Virtualization Framework (AVF) Detector.
 * Checks if the system environment supports kernel-level virtualization.
 */
public class AVFDetector {
    private static final String TAG = "OneCore-AVF";

    /**
     * Checks if the device is running Android 15 (API 35) or higher.
     */
    public static boolean isAndroid15Plus() {
        return Build.VERSION.SDK_INT >= 35;
    }

    /**
     * Checks if AVF (Android Virtualization Framework) is available on the device.
     */
    public static boolean isAVFAvailable(Context context) {
        if (!isAndroid15Plus()) return false;
        
        try {
            // Check for System Feature or attempt to load VirtualMachineManager
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_VIRTUALIZATION_FRAMEWORK);
        } catch (Exception e) {
            Log.e(TAG, "Failed to detect AVF system feature", e);
            return false;
        }
    }

    /**
     * Checks if the app has the MANAGE_VIRTUAL_MACHINE permission.
     */
    public static boolean hasVirtualMachinePermission(Context context) {
        if (!isAndroid15Plus()) return false;
        
        int result = context.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE");
        return result == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Comprehensive check for Android 15 Sandbox Readiness.
     */
    public static String getAVFStatus(Context context) {
        if (!isAndroid15Plus()) return "LEGACY_ANDROID";
        if (!isAVFAvailable(context)) return "AVF_NOT_SUPPORTED";
        if (hasVirtualMachinePermission(context)) return "READY";
        return "PERMISSION_REQUIRED";
    }
}
