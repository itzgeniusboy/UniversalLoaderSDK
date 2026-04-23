package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Method;

/**
 * Custom Instrumentation for OneCore Sandbox.
 * Intercepts Activity creation to inject the Guest ClassLoader and Resources.
 */
public class VAInstrumentation extends Instrumentation {
    private static final String TAG = "OneCore-Instrumentation";
    private final Instrumentation base;

    public VAInstrumentation(Instrumentation base) {
        this.base = base;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        // Redirection check for unregistered guest activities
        String targetActivity = intent.getStringExtra("target_activity");
        if (targetActivity != null) {
            Logger.i(TAG, "Redirection triggered for: " + targetActivity);
            cl = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();
            
            // Critical: If cl is null, the sandbox isn't ready
            if (cl == null) {
                Logger.e(TAG, "Guest ClassLoader is null! Sandbox boot sequence out of order.");
                return base.newActivity(cl, className, intent);
            }
            
            try {
                // Return instance of the REAL game activity
                Activity activity = (Activity) cl.loadClass(targetActivity).newInstance();
                Logger.d(TAG, "Successfully instantiated guest activity: " + targetActivity);
                return activity;
            } catch (Exception e) {
                Logger.e(TAG, "Failed to instantiate guest activity: " + targetActivity, e);
            }
        }
        
        return base.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        // Here we could inject guest resources if needed, but we handle it via LoadedApk hooks
        base.callActivityOnCreate(activity, icicle);
    }
}
