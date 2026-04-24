package com.onecore.sdk.core;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Manages virtual Application lifecycle.
 * Ensures target app's Application class is instantiated, patched, and initialized.
 */
public class ApplicationManager {
    private static final String TAG = "OneCore-AppMgr";
    private static Application sVirtualApp;
    private static final java.util.Map<String, Application> sAppMap = new java.util.HashMap<>();

    public static Application getVirtualApp() {
        return sVirtualApp;
    }

    public static Application getVirtualApp(String packageName) {
        return sAppMap.get(packageName);
    }

    public static Application bindApplication(Context hostContext, String packageName, String appClassName, ClassLoader cl, android.content.res.Resources res) {
        if (hostContext == null || packageName == null || cl == null || res == null) {
            Logger.e(TAG, "Cannot bind application: missing required parameters");
            return null;
        }

        if (sAppMap.containsKey(packageName)) return sAppMap.get(packageName);

        synchronized (ApplicationManager.class) {
            if (sAppMap.containsKey(packageName)) return sAppMap.get(packageName);
            
            try {
                String targetClassName = appClassName != null ? appClassName : "android.app.Application";
                Logger.i(TAG, "Lifecycle: Binding Application -> " + targetClassName + " for " + packageName);
                
                // 1. Instantiate Application
                Instrumentation instrumentation = new Instrumentation();
                Application app = instrumentation.newApplication(cl, targetClassName, hostContext);
                
                if (app == null) {
                    Logger.e(TAG, "Failed to instantiate Application class: " + targetClassName);
                    return null;
                }

                // 2. Deep Patch Application Context before attach
                ContextFixer.fix(app, packageName, cl, res);
                
                // 3. Attach Context
                try {
                    Method attach = Application.class.getDeclaredMethod("attach", Context.class);
                    attach.setAccessible(true);
                    attach.invoke(app, hostContext);
                } catch (Exception e) {
                    Logger.w(TAG, "Application.attach(Context) failed, trying variants");
                }
                
                // 4. Update ActivityThread.mInitialApplication to point to our virtual app
                updateActivityThreadApp(app);
                
                // Also update mBoundApplication if possible
                updateBoundApplication(app);
                
                // 5. Install Providers (Before Application.onCreate is standard)
                try {
                    android.content.pm.PackageInfo pi = com.onecore.sdk.core.pm.VirtualPackageManager.get().getClonedPackage(packageName);
                    if (pi != null && pi.providers != null) {
                        ProviderManager.installProviders(hostContext, app, packageName, java.util.Arrays.asList(pi.providers));
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Provider installation FAILED", e);
                }

                // 6. Lifecycle onCreate
                app.onCreate();
                
                synchronized (ApplicationManager.class) {
                    sVirtualApp = app;
                    sAppMap.put(packageName, app);
                }
                
                Logger.i(TAG, "Virtual Application successfully initialized: " + packageName);
                return app;
            } catch (Exception e) {
                Logger.e(TAG, "CRITICAL: Application Environment Binding FAILED", e);
                return null;
            }
        }
    }

    private static void updateBoundApplication(Application app) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method curAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            curAtMethod.setAccessible(true);
            Object activityThread = curAtMethod.invoke(null);

            if (activityThread != null) {
                Field mBoundAppField = atClass.getDeclaredField("mBoundApplication");
                mBoundAppField.setAccessible(true);
                Object mBoundApp = mBoundAppField.get(activityThread);
                if (mBoundApp != null) {
                    Field appField = mBoundApp.getClass().getDeclaredField("app");
                    appField.setAccessible(true);
                    appField.set(mBoundApp, app);
                    Logger.v(TAG, "mBoundApplication.app updated correctly.");
                }
            }
        } catch (Exception e) {
            Logger.w(TAG, "Failed to update mBoundApplication reference.");
        }
    }

    private static void updateActivityThreadApp(Application app) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method curAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            curAtMethod.setAccessible(true);
            Object activityThread = curAtMethod.invoke(null);

            if (activityThread != null) {
                Field mInitialAppField = atClass.getDeclaredField("mInitialApplication");
                mInitialAppField.setAccessible(true);
                mInitialAppField.set(activityThread, app);
                
                // Also update mAllApplications list
                try {
                    Field mAllAppsField = atClass.getDeclaredField("mAllApplications");
                    mAllAppsField.setAccessible(true);
                    java.util.List<Application> allApps = (java.util.List<Application>) mAllAppsField.get(activityThread);
                    if (!allApps.contains(app)) {
                        allApps.add(app);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Logger.w(TAG, "Failed to update ActivityThread global application state");
        }
    }
}
