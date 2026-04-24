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
            if (guestLoader != null) {
                Logger.d(TAG, "Forcing DexClassLoader for: " + realClassName);
                try {
                    // Force using the guest loader specifically for the target activity
                    Class<?> activityClass = guestLoader.loadClass(realClassName);
                    Activity activity = (Activity) activityClass.newInstance();
                    return activity;
                } catch (Exception e) {
                    Logger.e(TAG, "Manual instantiation failed, falling back to super.newActivity", e);
                    return super.newActivity(guestLoader, realClassName, targetIntent);
                }
            }
        }

        return super.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Logger.i(TAG, ">>> callActivityOnCreate: " + activity.getClass().getName());
        
        // 3. SECURE CONTEXT FIX (Resources/ClassLoader/PackageName injection)
        try {
            Intent intent = activity.getIntent();
            Intent targetIntent = intent.getParcelableExtra("EXTRA_TARGET_INTENT");
            if (targetIntent != null && targetIntent.getComponent() != null) {
                String pkgName = targetIntent.getComponent().getPackageName();
                ClassLoader guestLoader = CloneManager.getInstance().getClassLoader();
                android.content.res.Resources guestResources = CloneManager.getInstance().getResources();

                // Advanced Fix: Inject resources and classloader into the base context
                ContextFixer.fix(activity.getBaseContext(), pkgName, guestLoader, guestResources);
                
                // Also fix the activity instance's own fields
                ResourceInjector.inject(activity, guestResources);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Dynamic Context Fix Failed", e);
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
