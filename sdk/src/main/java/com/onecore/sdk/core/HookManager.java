package com.onecore.sdk.core;

import android.app.Instrumentation;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class HookManager {
    private static final String TAG = "OneCoreHookManager";

    public static void installInstrumentationHook() {
        try {
            Log.i(TAG, ">>> INITIATING OneCore SYSTEM HOOK (Phase 2) <<<");
            
            // 1. Get ActivityThread
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);
            
            if (activityThread == null) {
                Log.e(TAG, "!!! CRITICAL: ActivityThread.currentActivityThread() is NULL !!!");
                return;
            }

            // 2. Wrap Instrumentation
            Field instrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
            instrumentationField.setAccessible(true);
            Instrumentation baseInstrumentation = (Instrumentation) instrumentationField.get(activityThread);
            
            if (baseInstrumentation instanceof OneCoreInstrumentation) {
                Log.i(TAG, "Status: OneCoreInstrumentation already active.");
            } else {
                Log.i(TAG, "Injecting Custom Instrumentation. Base: " + (baseInstrumentation != null ? baseInstrumentation.getClass().getName() : "null"));
                OneCoreInstrumentation customInstrumentation = new OneCoreInstrumentation(baseInstrumentation);
                instrumentationField.set(activityThread, customInstrumentation);
                Log.i(TAG, ">>> OneCore: Instrumentation Hook SHOT successful. <<<");
                
                // Double check
                Instrumentation check = (Instrumentation) instrumentationField.get(activityThread);
                if (check instanceof OneCoreInstrumentation) {
                    Log.i(TAG, "Verification SUCCESS: OneCore is in control.");
                } else {
                    Log.e(TAG, "Verification FAILED: Hook was rejected or overwritten.");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "FATAL: OneCore Hook Deployment Failed", e);
        }
    }
}
