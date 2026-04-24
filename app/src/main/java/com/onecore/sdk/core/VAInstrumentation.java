package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import com.onecore.sdk.utils.Logger;

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
        
        String targetActivity = intent.getStringExtra("target_activity");
        if (targetActivity != null) {
            Logger.i(TAG, "Swapping Stub for Guest Activity: " + targetActivity);
            ClassLoader guestLoader = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();
            if (guestLoader != null) {
                return (Activity) guestLoader.loadClass(targetActivity).newInstance();
            }
        }
        
        return base.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        ClassLoader guestLoader = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();
        
        if (guestLoader != null && activity.getClass().getClassLoader() == guestLoader) {
            Logger.i(TAG, "Lifecycle: guest.onCreate -> " + activity.getClass().getName());
            patchActivityMetadata(activity);
        }
        
        base.callActivityOnCreate(activity, icicle);
    }

    private void patchActivityMetadata(Activity activity) {
        try {
            android.content.res.Resources guestRes = com.onecore.sdk.VirtualContainer.getInstance().getGuestResources();
            if (guestRes != null) {
                java.lang.reflect.Field mResourcesField = Activity.class.getDeclaredField("mResources");
                mResourcesField.setAccessible(true);
                mResourcesField.set(activity, guestRes);
            }
        } catch (Exception e) {
            Logger.w(TAG, "Failed to patch activity metadata: " + e.getMessage());
        }
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        base.callActivityOnResume(activity);
    }

    @Override
    public void callActivityOnStop(Activity activity) {
        base.callActivityOnStop(activity);
    }

    @Override
    public void callActivityOnPause(Activity activity) {
        base.callActivityOnPause(activity);
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

    @Override
    public void callApplicationOnCreate(android.app.Application app) {
        ClassLoader guestLoader = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();
        if (guestLoader != null && app.getClass().getClassLoader() == guestLoader) {
            Logger.i(TAG, "Guest Application OnCreate: " + app.getClass().getName());
            try {
                Object activityThread = java.lang.reflect.Method.forName("android.app.ActivityThread")
                        .getDeclaredMethod("currentActivityThread").invoke(null);
                EnvironmentHooker.setInitialApplication(activityThread, app);
            } catch (Exception e) {
                Logger.w(TAG, "Failed to sync guest application to ActivityThread: " + e.getMessage());
            }
        }
        base.callApplicationOnCreate(app);
    }
}
