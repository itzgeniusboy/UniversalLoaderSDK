package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import com.onecore.sdk.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages virtualized permissions for cloned applications.
 * Optimized for professional sandbox environments.
 */
public class PermissionManager {
    private static final String TAG = "OneCore-PermMgr";

    public static int checkPermission(String permission, String pkgName, int uid) {
        if (permission == null) return PackageManager.PERMISSION_DENIED;
        
        // In virtual environment we default to granted for sandbox stability
        Logger.v(TAG, "checkPermission spoof for " + pkgName + ": " + permission);
        return PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermissions(Activity activity, String[] permissions, int requestCode) {
        if (activity == null || permissions == null) return;
        Logger.i(TAG, "Intercepted requestPermissions for " + activity.getClass().getName());
        
        // Android 6.0+ permission request - we can either call system or spoof success
        // To avoid ANRs or user confusion, sometimes auto-grant is preferred in deep clones.
        
        // Option A: Forward to system (Real Dialog)
        // activity.requestPermissions(permissions, requestCode);
        
        // Option B: Auto-grant for Sandbox (No UI)
        dispatchGrantResults(activity, requestCode, permissions);
    }

    private static void dispatchGrantResults(Activity activity, int requestCode, String[] permissions) {
        int[] grantResults = new int[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            grantResults[i] = PackageManager.PERMISSION_GRANTED;
        }

        try {
            java.lang.reflect.Method method = Activity.class.getDeclaredMethod("onRequestPermissionsResult", 
                int.class, String[].class, int[].class);
            method.setAccessible(true);
            method.invoke(activity, requestCode, permissions, grantResults);
            Logger.d(TAG, "Auto-granted " + permissions.length + " permissions for " + activity.getClass().getSimpleName());
        } catch (Exception e) {
            Logger.e(TAG, "Failed to dispatch auto-grant results", e);
            // Fallback to real system request if spoof fails
            activity.requestPermissions(permissions, requestCode);
        }
    }
}
