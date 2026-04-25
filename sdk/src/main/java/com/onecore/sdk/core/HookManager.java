package com.onecore.sdk.core;

import android.app.Instrumentation;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class HookManager {
    private static final String TAG = "OneCoreHookManager";

    public static void installInstrumentationHook() {
        try {
            Log.i(TAG, "Installing Instrumentation Hook...");
            
            // 1. Get ActivityThread class
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            
            // 2. Get currentActivityThread instance
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);
            
            // 3. Get existing Instrumentation
            Field instrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
            instrumentationField.setAccessible(true);
            Instrumentation baseInstrumentation = (Instrumentation) instrumentationField.get(activityThread);
            
            // 4. Check if already hooked
            if (baseInstrumentation instanceof OneCoreInstrumentation) {
                Log.w(TAG, "Instrumentation already hooked.");
                return;
            }
            
            // 5. Replace with OneCoreInstrumentation
            OneCoreInstrumentation customInstrumentation = new OneCoreInstrumentation(baseInstrumentation);
            instrumentationField.set(activityThread, customInstrumentation);
            
            Log.i(TAG, "!!! OneCore HOOK SUCCESS !!! mInstrumentation replaced.");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to install Instrumentation Hook", e);
        }
    }
}
