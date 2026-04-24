package com.onecore.sdk.core;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import com.onecore.sdk.utils.Logger;

public class LoadedApkHook {
    private static final String TAG = "LoadedApkHook";

    public static void hook(String packageName, ClassLoader appClassLoader) {
        try {
            Object activityThread = getActivityThread();
            if (activityThread == null) return;

            Field mPackagesField = activityThread.getClass().getDeclaredField("mPackages");
            mPackagesField.setAccessible(true);
            Map<String, WeakReference<?>> mPackages = (Map<String, WeakReference<?>>) mPackagesField.get(activityThread);

            // Get or Create LoadedApk
            Object loadedApk = null;
            WeakReference<?> ref = mPackages.get(packageName);
            if (ref != null) {
                loadedApk = ref.get();
            }

            if (loadedApk == null) {
                Logger.d(TAG, "LoadedApk not found for " + packageName + ". Attempting to create one.");
                // We'll use getPackageInfoNoCheck or similar to generate a dummy one if possible,
                // or just patch the host one for simplicity in this stage.
                // For virtualization, we often find the host package and "hijack" it or add a new entry.
                String hostPackage = CloneManager.getInstance().getHostContext().getPackageName();
                ref = mPackages.get(hostPackage);
                if (ref != null) {
                    loadedApk = ref.get();
                }
            }

            if (loadedApk != null) {
                Field mClassLoaderField = loadedApk.getClass().getDeclaredField("mClassLoader");
                mClassLoaderField.setAccessible(true);
                mClassLoaderField.set(loadedApk, appClassLoader);
                
                // Important: Also update ApplicationInfo if necessary
                Field mApplicationInfoField = loadedApk.getClass().getDeclaredField("mApplicationInfo");
                mApplicationInfoField.setAccessible(true);
                ApplicationInfo hostAi = (ApplicationInfo) mApplicationInfoField.get(loadedApk);
                
                // We don't change the package name of the LoadedApk in ActivityThread yet 
                // to avoid confusing the system, but we ensure it uses OUR loader.
                Logger.i(TAG, "Successfully patched LoadedApk ClassLoader.");
            }

        } catch (Throwable e) {
            Logger.e(TAG, "LoadedApk Hook FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Object getActivityThread() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method current = atClass.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            return current.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }
}
