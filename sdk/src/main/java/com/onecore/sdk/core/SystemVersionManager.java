package com.onecore.sdk.core;

import android.os.Build;
import android.util.Log;

/**
 * Detects Android OS version and provides routing logic for API-level specific hooks.
 */
public class SystemVersionManager {
    private static final String TAG = "OneCore-VersionMan";

    public static boolean isAndroid10OrAbove() {
        return Build.VERSION.SDK_INT >= 29;
    }

    public static boolean isAndroid11OrAbove() {
        return Build.VERSION.SDK_INT >= 30;
    }

    public static boolean isAndroid12OrAbove() {
        return Build.VERSION.SDK_INT >= 31;
    }

    public static boolean isAndroid13OrAbove() {
        return Build.VERSION.SDK_INT >= 33;
    }

    public static boolean isAndroid14OrAbove() {
        // Android 14 is API 34
        return Build.VERSION.SDK_INT >= 34;
    }

    public static boolean isAndroid15OrAbove() {
        // Android 15 is API 35
        return Build.VERSION.SDK_INT >= 35;
    }

    public static String getAMServiceName() {
        if (isAndroid14OrAbove()) {
            return "activity_task";
        }
        return "activity";
    }

    public static String getIActivityManagerClassName() {
        if (isAndroid10OrAbove()) {
            return "android.app.IActivityTaskManager"; // Primary for moves since Android 10
        }
        return "android.app.IActivityManager";
    }
    
    public static void logVersionInfo() {
        Log.i(TAG, "OneCore-DEBUG: Running on Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
    }
}
