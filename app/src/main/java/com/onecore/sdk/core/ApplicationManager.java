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

    public static Application getVirtualApp() {
        return sVirtualApp;
    }

    public static Application bindApplication(Context hostContext, String packageName, String appClassName, ClassLoader cl, android.content.res.Resources res) {
        if (sVirtualApp != null) return sVirtualApp;

        synchronized (ApplicationManager.class) {
            if (sVirtualApp != null) return sVirtualApp;
            
            try {
                Logger.i(TAG, "Lifecycle: Binding Application -> " + appClassName);
                
                // 1. Instantiate Application
                Instrumentation instrumentation = new Instrumentation();
                Application app = instrumentation.newApplication(cl, appClassName, hostContext);
                
                // 2. Deep Patch Application Context before attach
                ContextFixer.fix(app, packageName, cl, res);
                
                // 3. Attach Context
                Method attach = Application.class.getDeclaredMethod("attach", Context.class);
                attach.setAccessible(true);
                attach.invoke(app, hostContext);
                
                // 4. Update ActivityThread.mInitialApplication to point to our virtual app
                updateActivityThreadApp(app);
                
                // 5. Lifecycle onCreate
                app.onCreate();
                sVirtualApp = app;
                
                Logger.i(TAG, "Virtual Application successfully initialized.");
                return app;
            } catch (Exception e) {
                Logger.e(TAG, "CRITICAL: Application Environment Binding FAILED", e);
                return null;
            }
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
