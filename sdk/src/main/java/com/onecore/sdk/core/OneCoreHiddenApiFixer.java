package com.onecore.sdk.core;

import android.util.Log;
import java.lang.reflect.Method;

/**
 * Bypasses Android's hidden API restrictions (Android 9-14+).
 * This is crucial for virtualization frameworks to use reflection on system classes.
 */
public class OneCoreHiddenApiFixer {
    private static final String TAG = "OneCore-HiddenApi";

    public static void bypass() {
        try {
            Log.i(TAG, "OneCore-DEBUG: Attempting Hidden API bypass...");
            
            // Using the VMRuntime.setHiddenApiExemptions method (FreeReflection pattern)
            Class<?> versionClass = Class.forName("android.os.Build$VERSION");
            int sdkInt = versionClass.getField("SDK_INT").getInt(null);
            
            if (sdkInt >= 28) {
                // Accessing VMRuntime via double reflection
                Method forName = Class.class.getDeclaredMethod("forName", String.class);
                Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);

                Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
                Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
                Object vmRuntime = getRuntime.invoke(null);
                
                Method setExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
                
                // Exempt all "L" (all classes) from hidden API check
                setExemptions.invoke(vmRuntime, (Object) new String[]{"L"});
                
                Log.i(TAG, "OneCore-DEBUG: Hidden API restriction removed for Android " + sdkInt);
            }
        } catch (Exception e) {
            Log.w(TAG, "Hidden API bypass failed (might be already bypassed or unsupported): " + e.getMessage());
        }
    }
}
