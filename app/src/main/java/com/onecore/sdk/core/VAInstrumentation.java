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
        // Redirection check for resources
        if (activity.getClass().getName().startsWith("com.pubg") || 
            activity.getClass().getName().startsWith("com.epicgames")) {
            
            Logger.i(TAG, "Patching resources for activity: " + activity.getClass().getName());
            // In a real sandbox, we would replace the activity's mResources/mContext here
            // But we already patched LoadedApk, which should cover getResources() calls.
        }
        
        base.callActivityOnCreate(activity, icicle);
    }

    @Override
    public android.app.Application newApplication(ClassLoader cl, String className, android.content.Context context) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        // If we are in the sandbox process, we want to return the REAL game Application class
        // This is usually called when the process starts, but we might need to trigger it manually
        return base.newApplication(cl, className, context);
    }
}
