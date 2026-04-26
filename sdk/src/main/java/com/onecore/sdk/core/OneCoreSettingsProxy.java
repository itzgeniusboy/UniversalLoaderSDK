package com.onecore.sdk.core;

import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;
import com.onecore.sdk.utils.ReflectionHelper;

/**
 * Spoofs system settings like adb_enabled to hide debugging from games.
 */
public class OneCoreSettingsProxy {
    private static final String TAG = "OneCore-Settings";

    public static void install() {
        Log.i(TAG, "OneCore-DEBUG: Setting up Secure Settings Proxy...");
        // This usually requires hooking ContentResolver or the specific IContentProvider
        // For simple cases, we can try to influence the cache.
    }

    public static Object spoof(String name, Object original) {
        if (name == null) return original;
        
        switch (name) {
            case Settings.Global.ADB_ENABLED:
            case Settings.Global.DEVELOPMENT_SETTINGS_ENABLED:
                return "0";
            case "usb_mass_storage_enabled":
                return "1";
            default:
                return original;
        }
    }
}
