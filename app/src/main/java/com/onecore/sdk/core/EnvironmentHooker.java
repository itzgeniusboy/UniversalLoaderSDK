package com.onecore.sdk.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import com.onecore.sdk.utils.Logger;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Orchestrates the application of hooks to the current process.
 * This ensures system services are proxied BEFORE the target app starts.
 */
public class EnvironmentHooker {
    private static final String TAG = "EnvironmentHooker";

    public static void apply(Context context, String fakePackageName) {
        try {
            Logger.i(TAG, "Applying Environment Hooks for " + fakePackageName);

            // 1. Hook ActivityThread primitives
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);

            // 2. Hook Package Manager Service
            hookPackageManager(activityThreadClass, fakePackageName);

            // 3. Hook Instrumentation (Interception of Activity Creation)
            hookInstrumentation(activityThread, activityThreadClass);

            // 4. Hook mPackages (LoadedApk spoofing)
            hookLoadedApk(activityThread, activityThreadClass, fakePackageName);

            Logger.i(TAG, "Deep Virtualization Hooks Applied Successfully.");
        } catch (Exception e) {
            Logger.e(TAG, "Global Hooking Failure", e);
        }
    }

    private static void hookPackageManager(Class<?> activityThreadClass, String fakeName) throws Exception {
        Method getPackageManager = activityThreadClass.getDeclaredMethod("getPackageManager");
        getPackageManager.setAccessible(true);
        Object originalPm = getPackageManager.invoke(null);

        Object proxyPm = PackageManagerHook.createProxy(originalPm, fakeName);
        
        Field sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        sPackageManagerField.set(null, proxyPm);
        
        Logger.d(TAG, "sPackageManager hooked.");
    }

    private static void hookInstrumentation(Object activityThread, Class<?> activityThreadClass) throws Exception {
        Field instrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
        instrumentationField.setAccessible(true);
        Object originalInstrumentation = instrumentationField.get(activityThread);

        // Replace with our Custom Instrumentation
        VAInstrumentation vaInstrumentation = new VAInstrumentation(originalInstrumentation);
        instrumentationField.set(activityThread, vaInstrumentation);
        
        Logger.d(TAG, "mInstrumentation HOOKED with VAInstrumentation.");
    }

    private static void hookLoadedApk(Object activityThread, Class<?> activityThreadClass, String packageName) throws Exception {
        Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);
        Map<?, ?> mPackages = (Map<?, ?>) mPackagesField.get(activityThread);
        
        // Find our host package in the map
        for (Object value : mPackages.values()) {
            Field mPackageNameField = value.getClass().getDeclaredField("mPackageName");
            mPackageNameField.setAccessible(true);
            mPackageNameField.set(value, packageName);
            
            Field mApplicationInfoField = value.getClass().getDeclaredField("mApplicationInfo");
            mApplicationInfoField.setAccessible(true);
            ApplicationInfo ai = (ApplicationInfo) mApplicationInfoField.get(value);
            ai.packageName = packageName;
            
            Logger.d(TAG, "LoadedApk successfully spoofed for: " + packageName);
        }

        // Also hook mInitialApplication
        Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
        mInitialApplicationField.setAccessible(true);
        // We don't set it to null, but we could replace it later if needed.
    }
}
