package com.onecore.sdk.utils;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Handles all critical permissions for OneCore Loader.
 * Ensures Overlay and Storage are granted before library injection.
 */
public class PermissionsHelper {
    
    public static final int REQUEST_CODE_OVERLAY = 1234;
    public static final int REQUEST_CODE_STORAGE = 5678;

    public static boolean hasAllPermissions(Context context) {
        return hasStoragePermission(context) && 
               hasOverlayPermission(context) && 
               hasUsageStatsPermission(context);
    }

    public static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE for virtual OBB/Data mapping
            return android.os.Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Internal standard permission request for legacy storage.
     */
    public static void requestStandardStorage(android.app.Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CODE_STORAGE);
        }
    }

    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    public static boolean hasUsageStatsPermission(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                    android.os.Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return true; // Fallback if system service fails
        }
    }

    /**
     * Triggers the appropriate settings UI for special permissions.
     * Fixed: Seamless one-by-one flow.
     */
    public static boolean requestNextPermission(Context context) {
        // 1. Storage Permission (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasStoragePermission(context)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return true;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !hasStoragePermission(context)) {
            if (context instanceof android.app.Activity) {
                requestStandardStorage((android.app.Activity) context);
                return true;
            }
        }

        // 2. Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasOverlayPermission(context)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        }

        // 3. Usage Stats
        if (!hasUsageStatsPermission(context)) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        }
        
        return false;
    }

    @Deprecated
    public static void requestSpecialPermissions(Context context) {
        requestNextPermission(context);
    }
}
