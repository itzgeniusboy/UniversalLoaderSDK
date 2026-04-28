package com.onecore.sdk;

import android.os.Build;
import com.onecore.sdk.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Advanced Hardware & Identity Spoofer.
 * Bypasses device-side bans and fingerprints by providing fake hardware meta-data.
 */
public class DeviceSpoofer {
    private static final String TAG = "OneCore-Spoofer";
    private static final Map<String, String> FAKE_DATA = new HashMap<>();

    static {
        // Standard high-end device profile
        FAKE_DATA.put("MODEL", "SM-S918B"); // Galaxy S23 Ultra
        FAKE_DATA.put("MANUFACTURER", "samsung");
        FAKE_DATA.put("BRAND", "samsung");
        FAKE_DATA.put("PRODUCT", "dm3q");
        FAKE_DATA.put("HARDWARE", "qcom");
    }

    public static String getModel() {
        return FAKE_DATA.get("MODEL");
    }

    public static String getManufacturer() {
        return FAKE_DATA.get("MANUFACTURER");
    }

    /**
     * Powerful: Spoofs system properties by hooking the Build class.
     */
    public void applySpoof() {
        try {
            Logger.i(TAG, "Applying Hardware Identity Mask...");
            
            setBuildField("MODEL", FAKE_DATA.get("MODEL"));
            setBuildField("MANUFACTURER", FAKE_DATA.get("MANUFACTURER"));
            setBuildField("BRAND", FAKE_DATA.get("BRAND"));
            setBuildField("PRODUCT", FAKE_DATA.get("PRODUCT"));
            setBuildField("HARDWARE", FAKE_DATA.get("HARDWARE"));
            setBuildField("DEVICE", FAKE_DATA.get("PRODUCT"));
            setBuildField("BOARD", FAKE_DATA.get("PRODUCT"));
            
            // Spoof Serial (deprecated but often still checked)
            if (Build.VERSION.SDK_INT < 26) {
                setBuildField("SERIAL", "0123456789ABCDEF");
            }
            
            Logger.i(TAG, "Identity Mask Success: Handset spoofed as " + FAKE_DATA.get("MODEL"));
        } catch (Exception e) {
            Logger.e(TAG, "Spoofing Failed: " + e.getMessage());
        }
    }

    private void setBuildField(String name, String value) {
        try {
            java.lang.reflect.Field field = Build.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception e) {
            Logger.v(TAG, "Could not spoof field: " + name);
        }
    }

    public static DeviceSpoofer getInstance() {
        return new DeviceSpoofer();
    }

    public void init(android.content.Context context) {
        applySpoof();
    }
}
