package com.onecore.sdk.core.hook;

import android.app.Application;
import android.app.Instrumentation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import com.onecore.sdk.utils.Logger;

public class ApplicationHook {
    private static final String TAG = "ApplicationHook";

    public static Application createApplication(String packageName) {
        try {
            Object at = getActivityThread();
            Object loadedApk = getLoadedApk(at, packageName);
            if (loadedApk == null) {
                Logger.e(TAG, "Could not find LoadedApk to create application for: " + packageName);
                return null;
            }

            // Check if application is already created
            Field mApplicationField = loadedApk.getClass().getDeclaredField("mApplication");
            mApplicationField.setAccessible(true);
            Application existingApp = (Application) mApplicationField.get(loadedApk);
            if (existingApp != null) {
                return existingApp;
            }

            Method makeApplication = loadedApk.getClass()
                    .getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);
            makeApplication.setAccessible(true);

            // Fetch the current instrumentation (the one we hooked)
            Field mInstrumentationField = at.getClass().getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation instrumentation = (Instrumentation) mInstrumentationField.get(at);

            Application app = (Application) makeApplication.invoke(
                    loadedApk,
                    false,
                    instrumentation
            );

            if (app != null) {
                Logger.i(TAG, "Virtual Application successfully bound to ActivityThread for: " + packageName);
            }

            return app;

        } catch (Throwable e) {
            Logger.e(TAG, "createApplication failed for " + packageName + ": " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private static Object getActivityThread() throws Exception {
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Method current = atClass.getDeclaredMethod("currentActivityThread");
        current.setAccessible(true);
        return current.invoke(null);
    }

    private static Object getLoadedApk(Object at, String packageName) throws Exception {
        Field mPackagesField = at.getClass().getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);

        Map<String, WeakReference<?>> mPackages =
                (Map<String, WeakReference<?>>) mPackagesField.get(at);

        WeakReference<?> ref = mPackages.get(packageName);
        return ref != null ? ref.get() : null;
    }
}
