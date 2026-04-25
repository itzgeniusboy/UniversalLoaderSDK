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
        setStaticField(Build.class, "BRAND", "POCO");
        setStaticField(Build.class, "MODEL", "POCO F5");
        setStaticField(Build.class, "DEVICE", "marble");
        setStaticField(Build.class, "PRODUCT", "marble");
        setStaticField(Build.class, "BOARD", "marble");
        setStaticField(Build.class, "SERIAL", "987654321ABC");
    }

    private static void setStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            // Removing 'final' modifier (works on most Android versions via reflection)
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            field.set(null, value);
        } catch (Throwable e) {
            Log.w(TAG, "Failed spoofing field: " + fieldName + " -> " + e.getMessage());
        }
    }
}
