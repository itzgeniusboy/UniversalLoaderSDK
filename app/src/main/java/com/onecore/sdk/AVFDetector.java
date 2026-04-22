package com.onecore.sdk;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * Android 15 Android Virtualization Framework (AVF) Detector.
 * Checks if the system environment supports kernel-level virtualization.
 * 
 * NOTE: Using string literal for FEATURE_VIRTUALIZATION_FRAMEWORK to avoid
 * compilation errors on environments with older SDK platforms.
 */
public class AVFDetector {
    private static final String TAG = "OneCore-AVF";
    
    // Constant value for android.content.pm.PackageManager.FEATURE_VIRTUALIZATION_FRAMEWORK
    // added in API 34.
    private static final String FEATURE_VIRTUALIZATION_FRAMEWORK = "android.software.vfs";

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
        // Feature check requires API 34+
        if (Build.VERSION.SDK_INT < 34) return false;
        
        try {
            // Using string literal to prevent "cannot find symbol" compilation error
            return context.getPackageManager().hasSystemFeature(FEATURE_VIRTUALIZATION_FRAMEWORK);
        } catch (Exception e) {
            Log.e(TAG, "Detection of AVF failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean isAVFAvailable() {
        Context context = OneCoreSDK.getContext();
        if (context == null) return false;
        return isAVFAvailable(context);
    }

    /**
     * Checks if the app has the MANAGE_VIRTUAL_MACHINE permission.
     */
    public static boolean hasVirtualMachinePermission(Context context) {
        if (Build.VERSION.SDK_INT < 35) return false;
        
        try {
            // Using literal string for permission added in Android 15
            int result = context.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE");
            return result == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
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
