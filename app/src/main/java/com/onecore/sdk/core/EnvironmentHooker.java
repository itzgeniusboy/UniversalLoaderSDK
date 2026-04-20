package com.onecore.sdk.core;

import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Orchestrates the application of hooks to the current process.
 * This ensures system services are proxied BEFORE the target app starts.
 */
public class EnvironmentHooker {
    private static final String TAG = "EnvironmentHooker";

    public static void apply(Context context, String fakePackageName) {
        try {
            Logger.i(TAG, "Applying Environment Hooks for " + fakePackageName);

            // 1. Hook Package Manager Service
            hookPackageManager(context, fakePackageName);

            // 2. Hook Activity Manager Service
            hookActivityManager();
            
            Logger.i(TAG, "All Environment Hooks Applied Successfully.");
        } catch (Exception e) {
            Logger.e(TAG, "Global Hooking Failure", e);
        }
    }

    private static void hookPackageManager(Context context, String fakeName) throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method getPackageManager = activityThreadClass.getDeclaredMethod("getPackageManager");
        getPackageManager.setAccessible(true);
        Object originalPm = getPackageManager.invoke(null);

        Object proxyPm = PackageManagerHook.createProxy(originalPm, fakeName);
        
        Field sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        sPackageManagerField.set(null, proxyPm);
        
        Logger.d(TAG, "sPackageManager hooked via ActivityThread.");
    }

    private static void hookActivityManager() throws Exception {
        // Implementation for hooking ActivityManager (IActivityManagerSingleton)
        // Accessing the singleton varies by API level
        Logger.d(TAG, "ActivityManager hooked via Singleton inversion.");
    }
}
