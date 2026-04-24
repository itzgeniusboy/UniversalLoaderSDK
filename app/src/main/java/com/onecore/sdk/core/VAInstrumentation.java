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
        
        // Redirection check disabled in favor of StubActivity Proxy pattern
        // String targetActivity = intent.getStringExtra("target_activity");
        // if (targetActivity != null) { ... }
        
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
        
        ClassLoader guestLoader = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();
        if (guestLoader != null) {
            Logger.i(TAG, "Virtual application mapping for: " + className);
            return base.newApplication(guestLoader, className, context);
        }
        
        return base.newApplication(cl, className, context);
    }
}
