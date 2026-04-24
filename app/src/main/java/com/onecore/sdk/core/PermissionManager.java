package com.onecore.sdk.core;

import android.content.Context;
import android.content.pm.PackageManager;
import com.onecore.sdk.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages virtualized permissions for cloned applications.
 */
public class PermissionManager {
    private static final String TAG = "OneCore-PermMgr";

    public static int checkPermission(String permission, String pkgName, int uid) {
        // Always grant for virtualization purposes to prevent immediate crashes
        Logger.v(TAG, "checkPermission spoof for " + pkgName + ": " + permission);
        return PackageManager.PERMISSION_GRANTED;
    }

    public static int checkSelfPermission(Context context, String permission) {
        Logger.v(TAG, "checkSelfPermission spoof: " + permission);
        return PackageManager.PERMISSION_GRANTED;
    }
}
