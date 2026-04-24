package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
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
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        String targetActivity = intent.getStringExtra("target_activity");
        if (targetActivity != null) {
            Logger.i(TAG, "Interacting with ActivityThread for: " + targetActivity);
            try {
                // Restore original intent for the guest activity
                String targetPackage = intent.getStringExtra("target_package");
                intent.setClassName(targetPackage, targetActivity);
                intent.removeExtra("target_activity");
                intent.removeExtra("target_package");

                ClassLoader guestLoader = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();
                if (guestLoader != null) {
                    // Load the real activity class
                    Class<?> activityClass = guestLoader.loadClass(targetActivity);
                    Activity activity = (Activity) activityClass.newInstance();
                    
                    // Hook into ActivityThread records to fix rendering/metadata
                    patchActivityThreadRecord(activity, targetPackage, targetActivity);
                    
                    return activity;
                }
            } catch (Exception e) {
                Logger.e(TAG, "System-driven launch swap failed: " + e.getMessage());
            }
        }
        
        return base.newActivity(cl, className, intent);
    }

    private void patchActivityThreadRecord(Activity activity, String pkg, String clazz) {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field mActivitiesField = activityThreadClass.getDeclaredField("mActivities");
            mActivitiesField.setAccessible(true);
            Map<Object, Object> mActivities = (Map<Object, Object>) mActivitiesField.get(activityThread);

            // We need to find the record that was just created for StubActivity
            // and update its fields to match the Guest Activity.
            for (Object record : mActivities.values()) {
                java.lang.reflect.Field activityField = record.getClass().getDeclaredField("activity");
                activityField.setAccessible(true);
                Object recordActivity = activityField.get(record);
                
                // If this record's activity is not yet set (or is the one we're creating), it's our target
                if (recordActivity == null || recordActivity == activity) {
                    Logger.d(TAG, "Patching ActivityClientRecord for " + clazz);
                    
                    // Update ActivityInfo if possible
                    try {
                        java.lang.reflect.Field infoField = record.getClass().getDeclaredField("activityInfo");
                        infoField.setAccessible(true);
                        android.content.pm.ActivityInfo info = (android.content.pm.ActivityInfo) infoField.get(record);
                        info.packageName = pkg;
                        info.name = clazz;
                    } catch (Exception ignored) {}

                    // The system will call attach() shortly after we return the activity.
                    // By then, the record in mActivities will point to our guest activity.
                    break;
                }
            }
        } catch (Exception e) {
            Logger.w(TAG, "ActivityThread Record Patching Failed: " + e.getMessage());
        }
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        ClassLoader guestLoader = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();
        
        if (guestLoader != null && activity.getClass().getClassLoader() == guestLoader) {
            String pkg = activity.getPackageName();
            Logger.i(TAG, "Lifecycle: guest.onCreate -> " + activity.getClass().getName());
            
            // Apply Metadata and Theme
            patchActivityMetadata(activity);
            
            // Try to set correct theme if not already set by manifest swap
            try {
                android.content.pm.PackageInfo info = com.onecore.sdk.VirtualContainer.getInstance().getClonedPackage(pkg);
                if (info != null && info.activities != null) {
                    for (android.content.pm.ActivityInfo ai : info.activities) {
                        if (ai.name.equals(activity.getClass().getName()) && ai.theme != 0) {
                            activity.setTheme(ai.theme);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
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
