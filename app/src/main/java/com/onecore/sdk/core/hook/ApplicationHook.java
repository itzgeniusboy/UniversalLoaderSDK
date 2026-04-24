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

    public static Application createApplication() {
        try {
            Object at = getActivityThread();
            Object loadedApk = getLoadedApk(at);
            if (loadedApk == null) {
                Logger.e(TAG, "Could not find LoadedApk to create application.");
                return null;
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
                // 🔥 Sync with ActivityThread
                Field mInitialApplicationField = at.getClass().getDeclaredField("mInitialApplication");
                mInitialApplicationField.setAccessible(true);
                mInitialApplicationField.set(at, app);

                Field mAllApplicationsField = at.getClass().getDeclaredField("mAllApplications");
                mAllApplicationsField.setAccessible(true);
                java.util.List<Application> allApps = (java.util.List<Application>) mAllApplicationsField.get(at);
                if (!allApps.contains(app)) {
                    allApps.add(app);
                }
                
                Logger.i(TAG, "Virtual Application successfully bound to ActivityThread.");
            }

            return app;

        } catch (Throwable e) {
            Logger.e(TAG, "createApplication failed: " + e.getMessage());
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

    private static Object getLoadedApk(Object at) throws Exception {
        Field mPackagesField = at.getClass().getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);

        Map<String, WeakReference<?>> mPackages =
                (Map<String, WeakReference<?>>) mPackagesField.get(at);

        // Preference: Return a LoadedApk that we've already patched
        for (WeakReference<?> ref : mPackages.values()) {
            Object loadedApk = ref.get();
            if (loadedApk != null) {
                return loadedApk;
            }
        }
        return null;
    }
}
