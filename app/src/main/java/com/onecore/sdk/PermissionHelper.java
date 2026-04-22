package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

/**
 * Permission Helper for Android 15 Virtualization.
 * Provides flows for Shizuku and ADB permission granting.
 */
public class PermissionHelper {
    private static final String TAG = "OneCore-Perm";
    private static final String AVF_PERMISSION = "android.permission.MANAGE_VIRTUAL_MACHINE";

    /**
     * Checks for AVF permission status and provides UI feedback.
     */
    public static void checkAndRequestAVFPermission(Context context) {
        if (AVFDetector.hasVirtualMachinePermission(context)) {
            Log.i(TAG, "AVF Permission already granted.");
            return;
        }

        Log.w(TAG, "Critical Permission Missing: MANAGE_VIRTUAL_MACHINE");
        // In a real app, this would show a dialog. Here we log the instructions.
        showPermissionInstructions(context);
    }

    /**
     * Logs and potentially displays ADB instructions.
     */
    public static void showPermissionInstructions(Context context) {
        String packageName = context.getPackageName();
        String command = "adb shell pm grant " + packageName + " " + AVF_PERMISSION;
        
        Log.i(TAG, "Run this command via ADB: " + command);
    }

    /**
     * Directs the user to Shizuku if they want to grant permissions on-device.
     */
    public static void openShizukuInstructions(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://shizuku.rikka.app/download/"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not open browser", e);
        }
    }

    /**
     * Method 5: User-Friendly Permission Grant Flow.
     * Shows a dialog (simulated via Log/Toast) with ADB command or Shizuku option.
     */
    public static void showAdvancedPermissionDialog(Context context) {
        String packageName = context.getPackageName();
        String adbCmd = "adb shell pm grant " + packageName + " " + AVF_PERMISSION;
        
        Log.i(TAG, "==== ANDROID 15 PERMISSION GUIDE ====");
        Log.i(TAG, "Step 1: Enable Wireless Debugging in Settings");
        Log.i(TAG, "Step 2: Run Command: " + adbCmd);
        Log.i(TAG, "Step 3: Restart Application");
        Log.i(TAG, "OR: Install Shizuku and grant auto-access via SDK Settings.");
        
        // In production, this would inflate a custom UI view with 'Copy Command' button
    }

    public static boolean hasAVFPermission() {
        return AVFDetector.hasVirtualMachinePermission(OneCoreSDK.getContext());
    }
}
