package com.onecore.sdk.core;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Entry point for system-level virtualization hooks.
 */
public class OneCoreActivityThreadHook {
    private static final String TAG = "OneCore-ATHook";

    public static void install(Context context) {
        try {
            Log.i(TAG, ">>> INITIATING OneCore SYSTEM HOOK <<<");
            
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);

            // 1. Hook Instrumentation
            Field instrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
            instrumentationField.setAccessible(true);
            android.app.Instrumentation baseInstrumentation = (android.app.Instrumentation) instrumentationField.get(activityThread);
            
            OneCoreInstrumentation customInstrumentation = new OneCoreInstrumentation(baseInstrumentation);
            instrumentationField.set(activityThread, customInstrumentation);
            
            // 2. Hook Service Proxies
            OneCoreAMSProxy.install(context.getPackageName());
            OneCorePackageManagerProxy.install();
            
            Log.i(TAG, "OneCore-DEBUG: System hooks successfully applied.");
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: System hook FAILED !!!", e);
        }
    }
}
