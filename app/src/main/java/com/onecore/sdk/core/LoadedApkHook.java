package com.onecore.sdk.core;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import com.onecore.sdk.utils.Logger;

public class LoadedApkHook {
    private static final String TAG = "LoadedApkHook";

    public static void hook(ClassLoader appClassLoader) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");

            Method currentMethod = atClass.getDeclaredMethod("currentActivityThread");
            currentMethod.setAccessible(true);

            Object activityThread = currentMethod.invoke(null);

            Field mPackagesField = atClass.getDeclaredField("mPackages");
            mPackagesField.setAccessible(true);

            Map<String, WeakReference<?>> mPackages =
                    (Map<String, WeakReference<?>>) mPackagesField.get(activityThread);

            for (Map.Entry<String, WeakReference<?>> entry : mPackages.entrySet()) {
                Object loadedApk = entry.getValue().get();
                if (loadedApk == null) continue;

                Class<?> loadedApkClass = loadedApk.getClass();
                Field mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader");
                mClassLoaderField.setAccessible(true);
                mClassLoaderField.set(loadedApk, appClassLoader);
                Logger.d(TAG, "Hooked LoadedApk for package: " + entry.getKey());
            }

        } catch (Throwable e) {
            Logger.e(TAG, "LoadedApk Hook FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
