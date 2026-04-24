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
        
        // In virtual environment we default to granted for sandbox stability.
        // For production, we may want to check real system grant status but often 
        // games need these granted automatically to skip complex UI flows.
        Logger.v(TAG, "checkPermission spoof for " + pkgName + ": " + permission);
        return PackageManager.PERMISSION_GRANTED;
    }

    public static int checkSelfPermission(Context context, String permission) {
        if (permission == null) return PackageManager.PERMISSION_DENIED;
        Logger.v(TAG, "checkSelfPermission spoof: " + permission);
        return PackageManager.PERMISSION_GRANTED;
    }

    public static boolean shouldShowRequestPermissionRationale(Activity activity, String permission) {
        Logger.v(TAG, "shouldShowRequestPermissionRationale spoof for: " + permission);
        return false; // Prevent annoying dialog rationales
    }

    public static void requestPermissions(Activity activity, String[] permissions, int requestCode) {
        if (activity == null || permissions == null) return;
        
        // Android 10+ location permissions often need special handling if not requested correctly
        Logger.i(TAG, "Requesting " + permissions.length + " permissions for " + activity.getClass().getSimpleName());
        
        // Final compatibility: We'll attempt a silent grant first for the sandbox
        if (shouldAutoGrant()) {
            dispatchGrantResults(activity, requestCode, permissions);
        } else {
            // Otherwise, proxy to the real system if the user needs to see it
            activity.requestPermissions(permissions, requestCode);
        }
    }

    private static boolean shouldAutoGrant() {
        // In a high-performance sandbox, we usually auto-grant to avoid UI interrupts
        return true;
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
