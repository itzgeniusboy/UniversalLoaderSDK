package com.onecore.sdk.core;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Method;

/**
 * Manages virtual Application lifecycle.
 * Ensures target app's Application class is instantiated and initialized.
 */
public class ApplicationManager {
    private static final String TAG = "OneCore-AppMgr";
    private static Application sVirtualApp;

    public static Application getVirtualApp() {
        return sVirtualApp;
    }

    public static void bindApplication(Context hostContext, String packageName, String appClassName, ClassLoader cl) {
        try {
            Logger.i(TAG, "Binding Virtual Application: " + appClassName);
            
            Instrumentation instrumentation = new Instrumentation();
            Application app = instrumentation.newApplication(cl, appClassName, hostContext);
            
            // Invoke attachBaseContext via reflection
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            attach.invoke(app, hostContext);
            
            app.onCreate();
            sVirtualApp = app;
            
            Logger.i(TAG, "Virtual Application successfully started.");
        } catch (Exception e) {
            Logger.e(TAG, "Application Binding FAILED", e);
        }
    }
}
