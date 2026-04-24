package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import java.lang.reflect.Field;
import java.util.Map;
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
        Logger.i(TAG, "VAInstrumentation Proxy initialized and active.");
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        String targetActivity = intent.getStringExtra("target_activity");

        if (targetActivity != null) {
            Logger.i(TAG, "Restoring Virtual Activity: " + targetActivity);
            try {
                String targetPackage = intent.getStringExtra("target_package");
                Intent originalIntent = intent.getParcelableExtra("original_intent");
                
                // Restore original intent for the guest activity if it exists
                if (originalIntent != null) {
                    intent.fillIn(originalIntent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_ACTION | Intent.FILL_IN_DATA | Intent.FILL_IN_CATEGORIES);
                }
                
                // Ensure target remains set to the correct package/class
                intent.setClassName(targetPackage != null ? targetPackage : className, targetActivity);
                
                // Clean up stub extras to prevent guest app from seeing them
                intent.removeExtra("target_activity");
                intent.removeExtra("target_package");
                intent.removeExtra("original_intent");

                ClassLoader appCl = getVirtualClassLoader(); // IMPORTANT
                return super.newActivity(appCl, targetActivity, intent);

            } catch (Exception e) {
                Logger.e(TAG, "System-driven launch swap failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return super.newActivity(cl, className, intent);
    }

    private ClassLoader getVirtualClassLoader() {
        return CloneManager.getInstance().getClassLoader(); 
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Logger.i(TAG, ">>> HOOK ACTIVE: callActivityOnCreate for " + activity.getClass().getName());
        inject(activity);
        super.callActivityOnCreate(activity, icicle);
    }

    private void inject(Activity activity) {
        try {
            Context baseCtx = activity.getBaseContext();
            Resources res = CloneManager.getInstance().getResources();

            if (res != null) {
                // Try multiple fields as different Android versions might name it differently or move it
                try {
                    Field mResources = Context.class.getDeclaredField("mResources");
                    mResources.setAccessible(true);
                    mResources.set(baseCtx, res);
                } catch (NoSuchFieldException e) {
                    // Fallback for some versions
                    try {
                        Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
                        Field mResourcesImpl = contextImplClass.getDeclaredField("mResources");
                        mResourcesImpl.setAccessible(true);
                        mResourcesImpl.set(baseCtx, res);
                    } catch (Exception ignored) {}
                }
                
                // Also set it on the activity itself
                try {
                    Field mActivityResources = Activity.class.getDeclaredField("mResources");
                    mActivityResources.setAccessible(true);
                    mActivityResources.set(activity, res);
                } catch (Exception ignored) {}
            }

        } catch (Throwable e) {
            Logger.e(TAG, "Injection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
    }

    @Override
    public void callActivityOnStop(Activity activity) {
        super.callActivityOnStop(activity);
    }

    @Override
    public void callActivityOnPause(Activity activity) {
        super.callActivityOnPause(activity);
    }

    /**
     * Intercept Activity launch at the client side.
     * This is a hidden method in Instrumentation.
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        Logger.i(TAG, ">>> HOOK ACTIVE: execStartActivity SYSTEM-LAUNCH -> " + (intent.getComponent() != null ? intent.getComponent().getClassName() : intent.toString()));
        
        // Use reflection to call the base execStartActivity as it's hidden
        try {
            java.lang.reflect.Method execMethod = Instrumentation.class.getDeclaredMethod(
                    "execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class);
            execMethod.setAccessible(true);
            return (ActivityResult) execMethod.invoke(base, who, contextThread, token, target, intent, requestCode, options);
        } catch (Exception e) {
            Logger.e(TAG, "execStartActivity hook failed: " + e.getMessage());
            return null;
        }
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
                Object activityThread = Class.forName("android.app.ActivityThread")
                        .getDeclaredMethod("currentActivityThread").invoke(null);
                EnvironmentHooker.setInitialApplication(activityThread, app);
            } catch (Exception e) {
                Logger.w(TAG, "Failed to sync guest application to ActivityThread: " + e.getMessage());
            }
        }
        base.callApplicationOnCreate(app);
    }
}
