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

    private static String sActivePackage;

    public static Application createApplication() {
        if (sActivePackage == null) {
            // Try to auto-derive from LoadedApk map if only one guest app is loaded
            try {
                Object at = getActivityThread();
                Field mPackagesField = at.getClass().getDeclaredField("mPackages");
                mPackagesField.setAccessible(true);
                Map<String, WeakReference<?>> mPackages = (Map<String, WeakReference<?>>) mPackagesField.get(at);
                String hostPkg = com.onecore.sdk.OneCoreSDK.getContext().getPackageName();
                for (String pkg : mPackages.keySet()) {
                    if (!pkg.equals(hostPkg)) {
                        sActivePackage = pkg;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        
        if (sActivePackage != null) {
            return createApplication(sActivePackage);
        }
        return null;
    }

    public static Application createApplication(String packageName) {
        sActivePackage = packageName;
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
