package com.onecore.sdk.core;

import android.os.Build;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Spoofs android.os.Build fields to bypass device checks in guest apps.
 */
public class OneCoreBuildProxy {
    private static final String TAG = "OneCore-BuildProxy";

    public static void spoof() {
        Log.i(TAG, "OneCore-DEBUG: Spoofing Build info...");
        setStaticField(Build.class, "MANUFACTURER", "Xiaomi");
        setStaticField(Build.class, "BRAND", "Xiaomi");
        setStaticField(Build.class, "MODEL", "23049PCD8G");
        setStaticField(Build.class, "DEVICE", "marble");
        setStaticField(Build.class, "PRODUCT", "marble");
        setStaticField(Build.class, "BOARD", "marble");
        setStaticField(Build.class, "HARDWARE", "qcom");
        setStaticField(Build.class, "FINGERPRINT", "Xiaomi/marble/marble:13/TKQ1.221114.001/V14.0.24.0.TMRMIXM:user/release-keys");
        setStaticField(Build.class, "SERIAL", "0123456789ABCDEF");
    }

    private static void setStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            try {
                // Removing 'final' modifier
                Field modifiersField = Field.class.getDeclaredField("accessFlags"); 
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (Exception e) {
                // Fallback for different field names
                try {
                    Field modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                } catch (Exception ignored) {}
            }

            field.set(null, value);
        } catch (Throwable e) {
            Log.w(TAG, "Failed spoofing field: " + fieldName);
        }
    }
}
