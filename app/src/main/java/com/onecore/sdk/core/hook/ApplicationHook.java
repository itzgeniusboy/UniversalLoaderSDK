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
            Object loadedApk = getLoadedApk();
            if (loadedApk == null) {
                Logger.e(TAG, "Could not find LoadedApk to create application.");
                return null;
            }

            Method makeApplication = loadedApk.getClass()
                    .getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);

            makeApplication.setAccessible(true);

            // Using a plain instrumentation here to avoid recursion if hooked
            Application app = (Application) makeApplication.invoke(
                    loadedApk,
                    false,
                    new Instrumentation()
            );

            return app;

        } catch (Throwable e) {
            Logger.e(TAG, "createApplication failed: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private static Object getLoadedApk() throws Exception {
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Method current = atClass.getDeclaredMethod("currentActivityThread");
        current.setAccessible(true);

        Object at = current.invoke(null);

        Field mPackagesField = atClass.getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);

        Map<String, WeakReference<?>> mPackages =
                (Map<String, WeakReference<?>>) mPackagesField.get(at);

        // In a real hook, we'd find the correct one for the guest.
        // For now, we take the first available or the one we patched recently.
        for (WeakReference<?> ref : mPackages.values()) {
            Object loadedApk = ref.get();
            if (loadedApk != null) {
                return loadedApk;
            }
        }

        return null;
    }
}
