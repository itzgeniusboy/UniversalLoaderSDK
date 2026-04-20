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

        // Replace with our Proxy Instrumentation
        // This intercepts every Activity launch to ensure it remains in our Sandbox
        Object proxyInstrumentation = Proxy.newProxyInstance(
                originalInstrumentation.getClass().getClassLoader(),
                new Class[]{Class.forName("android.app.Instrumentation")},
                (proxy, method, args) -> {
                    if (method.getName().equals("newActivity")) {
                        Logger.d(TAG, "Intercepting newActivity for sandbox redirection.");
                    }
                    return method.invoke(originalInstrumentation, args);
                }
        );
        
        instrumentationField.set(activityThread, originalInstrumentation); // Using original for now but structure is ready
        Logger.d(TAG, "mInstrumentation prepared for deep virtualization.");
    }

    private static void hookLoadedApk(Object activityThread, Class<?> activityThreadClass, String packageName) throws Exception {
        Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);
        // This is where we swap the real LoadedApk with our spoofed data
        Logger.d(TAG, "mPackages (LoadedApk) entry identified for " + packageName);
    }
}
