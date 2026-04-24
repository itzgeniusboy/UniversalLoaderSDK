package com.onecore.sdk.core;

import android.app.Instrumentation;
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

    public static void apply(Context context, String fakePackageName, String virtualPath) {
        try {
            Logger.i(TAG, "Applying Environment Hooks for " + fakePackageName);

            // 1. Hook ActivityThread primitives
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);

            // 2. Hook Package Manager Service
            hookPackageManager(activityThreadClass, fakePackageName, virtualPath);

            // 2.5 Hook Activity Manager Service
            hookActivityManager();
            hookActivityTaskManager();

            // 3. Hook Instrumentation (Interception of Activity Creation)
            hookInstrumentation(activityThread, activityThreadClass);

            // 4. Hook mPackages (LoadedApk spoofing)
            hookLoadedApk(activityThread, activityThreadClass, fakePackageName);

            // 5. Install Guest Providers
            installGuestProviders(activityThread, activityThreadClass, fakePackageName);

            Logger.i(TAG, "Deep Virtualization Hooks Applied Successfully.");
        } catch (Exception e) {
            Logger.e(TAG, "Global Hooking Failure", e);
        }
    }

    private static void installGuestProviders(Object activityThread, Class<?> activityThreadClass, String packageName) {
        try {
            Logger.i(TAG, "Installing Guest Providers for " + packageName);
            android.content.pm.PackageInfo info = com.onecore.sdk.VirtualContainer.getInstance().getClonedPackage(packageName);
            if (info == null || info.providers == null) return;

            Method installContentProviders = activityThreadClass.getDeclaredMethod("installContentProviders", android.content.Context.class, java.util.List.class);
            installContentProviders.setAccessible(true);
            
            java.util.List<android.content.pm.ProviderInfo> providers = java.util.Arrays.asList(info.providers);
            installContentProviders.invoke(activityThread, com.onecore.sdk.OneCoreSDK.getContext(), providers);
            
            Logger.d(TAG, "Guest Providers installed: " + info.providers.length);
        } catch (Exception e) {
            Logger.e(TAG, "Provider Installation Failure", e);
        }
    }

    private static void hookActivityManager() throws Exception {
        Object gDefault = null;
        try {
            // Android 8.0+
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Field iActivityManagerSingletonField = activityManagerClass.getDeclaredField("IActivityManagerSingleton");
            iActivityManagerSingletonField.setAccessible(true);
            gDefault = iActivityManagerSingletonField.get(null);
        } catch (Exception e) {
            // Older versions
            Class<?> amNativeClass = Class.forName("android.app.ActivityManagerNative");
            Field gDefaultField = amNativeClass.getDeclaredField("gDefault");
            gDefaultField.setAccessible(true);
            gDefault = gDefaultField.get(null);
        }

        if (gDefault == null) return;

        Class<?> singletonClass = Class.forName("android.util.Singleton");
        Method getMethod = singletonClass.getDeclaredMethod("get");
        getMethod.setAccessible(true);
        Object originalAm = getMethod.invoke(gDefault);

        if (originalAm == null) return;

        Object proxyAm = ActivityManagerHook.createProxy(originalAm);
        
        Field mInstanceField = singletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);
        mInstanceField.set(gDefault, proxyAm);
        
        Logger.d(TAG, "IActivityManager hooked.");
    }

    private static void hookActivityTaskManager() {
        try {
            Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
            Field singletonField = atmClass.getDeclaredField("IActivityTaskManagerSingleton");
            singletonField.setAccessible(true);
            Object singleton = singletonField.get(null);

            Class<?> singletonClass = Class.forName("android.util.Singleton");
            Method getMethod = singletonClass.getDeclaredMethod("get");
            getMethod.setAccessible(true);
            Object originalAtm = getMethod.invoke(singleton);

            if (originalAtm != null) {
                Object proxyAtm = ActivityManagerHook.createProxy(originalAtm);
                Field mInstanceField = singletonClass.getDeclaredField("mInstance");
                mInstanceField.setAccessible(true);
                mInstanceField.set(singleton, proxyAtm);
                Logger.d(TAG, "IActivityTaskManager hooked.");
            }
        } catch (Exception e) {
            Logger.d(TAG, "ActivityTaskManager not supported or hook failed: " + e.getMessage());
        }
    }

    private static void hookPackageManager(Class<?> activityThreadClass, String fakeName, String virtualPath) throws Exception {
        Method getPackageManager = activityThreadClass.getDeclaredMethod("getPackageManager");
        getPackageManager.setAccessible(true);
        Object originalPm = getPackageManager.invoke(null);
        if (originalPm == null) return;

        Object proxyPm = PackageManagerHook.createProxy(originalPm, fakeName, virtualPath);
        
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
        VAInstrumentation vaInstrumentation = new VAInstrumentation((Instrumentation) originalInstrumentation);
        instrumentationField.set(activityThread, vaInstrumentation);
        
        Logger.d(TAG, "mInstrumentation HOOKED with VAInstrumentation.");
    }

    private static void hookLoadedApk(Object activityThread, Class<?> activityThreadClass, String packageName) throws Exception {
        Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);
        Map<?, ?> mPackages = (Map<?, ?>) mPackagesField.get(activityThread);
        
        ClassLoader guestLoader = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();

        // Find our host package in the map
        for (Object value : mPackages.values()) {
            Object loadedApk = ((java.lang.ref.WeakReference<?>) value).get();
            if (loadedApk == null) continue;

            Field mPackageNameField = loadedApk.getClass().getDeclaredField("mPackageName");
            mPackageNameField.setAccessible(true);
            mPackageNameField.set(loadedApk, packageName);
            
            Field mApplicationInfoField = loadedApk.getClass().getDeclaredField("mApplicationInfo");
            mApplicationInfoField.setAccessible(true);
            ApplicationInfo ai = (ApplicationInfo) mApplicationInfoField.get(loadedApk);
            ai.packageName = packageName;
            
            // Critical for rendering and ClassLoader isolation:
            if (guestLoader != null) {
                Field mClassLoaderField = loadedApk.getClass().getDeclaredField("mClassLoader");
                mClassLoaderField.setAccessible(true);
                mClassLoaderField.set(loadedApk, guestLoader);
            }

            // Force recreation of resources associated with this LoadedApk
            try {
                Field mResourcesField = loadedApk.getClass().getDeclaredField("mResources");
                mResourcesField.setAccessible(true);
                mResourcesField.set(loadedApk, null);
                
                Field mResDirField = loadedApk.getClass().getDeclaredField("mResDir");
                mResDirField.setAccessible(true);
                // mResDir would be updated if we wanted to point to a different APK path
            } catch (Exception ignored) {}
            
            Logger.d(TAG, "LoadedApk successfully spoofed & ClassLoader injected for: " + packageName);
        }
    }

    public static void setInitialApplication(Object activityThread, android.app.Application app) {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(activityThread, app);
            Logger.i(TAG, "mInitialApplication swapped to Guest instance.");
        } catch (Exception e) {
            Logger.w(TAG, "Failed to swap mInitialApplication: " + e.getMessage());
        }
    }
}
