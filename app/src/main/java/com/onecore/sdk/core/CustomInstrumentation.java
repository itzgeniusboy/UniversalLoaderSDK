package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * FINAL Instrumentation Hook for Phase 1.
 * Ensures REAL Activity is loaded using the guest ClassLoader (DexClassLoader).
 * Fixes the "Black Screen" by forcing correct ClassLoader and Resources during Activity creation.
 */
public class CustomInstrumentation extends Instrumentation {
    private static final String TAG = "OneCore-Instrumentation";
    private final Instrumentation mBase;

    public CustomInstrumentation(Instrumentation base) {
        this.mBase = base;
        // Optionally bind mBase context if needed
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        // 1. Resolve REAL Activity from Intent if it was redirected
        Intent targetIntent = intent.getParcelableExtra("EXTRA_TARGET_INTENT");
        if (targetIntent != null && targetIntent.getComponent() != null) {
            String realClassName = targetIntent.getComponent().getClassName();
            String realPackageName = targetIntent.getComponent().getPackageName();
            Logger.i(TAG, "Intercepting Activity Creation: " + className + " -> " + realClassName);
            
            // 2. LOAD USING GUEST CLASSLOADER (DexClassLoader)
            ClassLoader guestLoader = CloneManager.getInstance().getClassLoader();
            android.content.res.Resources guestResources = CloneManager.getInstance().getResources();

            if (guestLoader != null) {
                // Ensure Virtual Application is correctly initialized before any Activity arises
                android.content.pm.PackageInfo pi = com.onecore.sdk.core.pm.VirtualPackageManager.get().getClonedPackage(realPackageName);
                if (pi != null && pi.applicationInfo != null) {
                    String appClass = pi.applicationInfo.className;
                    if (appClass == null) appClass = "android.app.Application";
                    ApplicationManager.bindApplication(CloneManager.getInstance().getHostContext(), realPackageName, appClass, guestLoader, guestResources);
                }

                Logger.d(TAG, "Forcing DexClassLoader for: " + realClassName);
                try {
                    // Force using the guest loader specifically for the target activity
                    Class<?> activityClass = guestLoader.loadClass(realClassName);
                    Activity activity = (Activity) activityClass.newInstance();
                    
                    // Pre-bind context if possible
                    ContextManager.bindContext(activity, realPackageName, guestLoader, guestResources);
                    
                    return activity;
                } catch (Exception e) {
                    Logger.e(TAG, "Manual instantiation failed for " + realClassName + ", falling back to super.newActivity", e);
                    try {
                        return super.newActivity(guestLoader, realClassName, targetIntent);
                    } catch (Exception ex) {
                        Logger.e(TAG, "Fallback instantiation also failed", ex);
                        throw ex;
                    }
                }
            }
        }

        return super.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Logger.i(TAG, ">>> Final callActivityOnCreate: " + activity.getClass().getName());
        
        try {
            Intent intent = activity.getIntent();
            Intent targetIntent = intent.getParcelableExtra("EXTRA_TARGET_INTENT");
            if (targetIntent != null && targetIntent.getComponent() != null) {
                String pkgName = targetIntent.getComponent().getPackageName();
                ClassLoader guestLoader = CloneManager.getInstance().getClassLoader();
                android.content.res.Resources guestResources = CloneManager.getInstance().getResources();

                // 1. Deep Context FIX
                ContextFixer.fix(activity.getBaseContext(), pkgName, guestLoader, guestResources);
                
                // 2. Resource & Theme Injection
                int theme = 0;
                android.content.pm.ActivityInfo ai = com.onecore.sdk.core.pm.VirtualPackageManager.resolveActivity(pkgName, activity.getClass().getName());
                if (ai != null) theme = ai.getThemeResource();
                
                ResourceInjector.inject(activity, guestResources, theme);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Final Environment Fix Failed", e);
        }
        
        try {
            mBase.callActivityOnCreate(activity, icicle);
        } catch (Throwable e) {
            Logger.e(TAG, "Activity callActivityOnCreate CRASHED", e);
            throw e;
        }
    }

    private Field getField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    // Capture startActivity calls to redirect them to StubActivity
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        intent = wrapIntent(who, intent);
        try {
            Method method = Instrumentation.class.getDeclaredMethod("execStartActivity", 
                Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class, int.class, Bundle.class);
            method.setAccessible(true);
            return (ActivityResult) method.invoke(mBase, who, contextThread, token, target, intent, requestCode, options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, String target,
            Intent intent, int requestCode, Bundle options) {
        
        intent = wrapIntent(who, intent);
        try {
            Method method = Instrumentation.class.getDeclaredMethod("execStartActivity", 
                Context.class, IBinder.class, IBinder.class, String.class, Intent.class, int.class, Bundle.class);
            method.setAccessible(true);
            return (ActivityResult) method.invoke(mBase, who, contextThread, token, target, intent, requestCode, options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Intent wrapIntent(Context who, Intent intent) {
        if (intent != null && intent.getComponent() != null) {
            String pkg = intent.getComponent().getPackageName();
            if (CloneManager.getInstance().getClonedPackage(pkg) != null) {
                Intent stub = new Intent();
                stub.setClassName(who.getPackageName(), "com.onecore.sdk.core.StubActivity");
                stub.putExtra("EXTRA_TARGET_INTENT", new Intent(intent));
                stub.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return stub;
            }
        }
        return intent;
    }
}
