package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.onecore.sdk.utils.Logger;

/**
 * Custom Instrumentation for OneCore Sandbox.
 * Intercepts Activity creation to inject the Guest ClassLoader and Resources.
 */
public class VAInstrumentation extends Instrumentation {
    private static final String TAG = "OneCore-VA";
    private final Instrumentation base;

    public VAInstrumentation(Instrumentation base) {
        this.base = base;
        Logger.i(TAG, "VAInstrumentation Proxy initialized and active.");
    }

    // 🔥 CORE REDIRECTION
    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        Logger.d(TAG, "newActivity: " + className);
        Intent target = intent.getParcelableExtra("_VA_TARGET_");
        
        // If the className is StubActivity but we have a target, we MUST redirect
        if (className.contains("StubActivity") && target != null && target.getComponent() != null) {
            String targetActivity = target.getComponent().getClassName();
            cl = CloneManager.getInstance().getClassLoader();
            className = targetActivity;
            intent = target;
            Logger.i(TAG, "Instrumentation Redirection [Stub -> Real]: " + className);
        } else if (target != null && target.getComponent() != null) {
             // Already swapped by HCallback maybe? Or just a nested launch.
             cl = CloneManager.getInstance().getClassLoader();
             className = target.getComponent().getClassName();
             intent = target;
        }

        return super.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Logger.i(TAG, ">>> callActivityOnCreate: " + activity.getClass().getName());
        injectContext(activity);
        try {
            base.callActivityOnCreate(activity, icicle);
        } catch (Throwable e) {
            Logger.e(TAG, "callActivityOnCreate Failed for " + activity.getClass().getName(), e);
            throw e;
        }
    }

    // 🔥 CRITICAL: Context Fix
    private void injectContext(Activity activity) {
        try {
            Context baseContext = activity.getBaseContext();
            android.content.res.Resources res = CloneManager.getInstance().getResources();
            ClassLoader cl = CloneManager.getInstance().getClassLoader();

            if (res != null) {
                Logger.d(TAG, "Aggressive Context Injection for: " + activity.getClass().getName());

                // 1. Patch ContextImpl
                try {
                    Field mResources = baseContext.getClass().getDeclaredField("mResources");
                    mResources.setAccessible(true);
                    mResources.set(baseContext, res);
                    
                    Field mTheme = baseContext.getClass().getDeclaredField("mTheme");
                    mTheme.setAccessible(true);
                    mTheme.set(baseContext, null); // Force theme re-inflation
                } catch (Exception e) {
                    Logger.e(TAG, "ContextImpl field patch failed: " + e.getMessage());
                }
                
                // 2. Patch Activity instance
                try {
                    Field mActivityResources = Activity.class.getDeclaredField("mResources");
                    mActivityResources.setAccessible(true);
                    mActivityResources.set(activity, res);
                } catch (Exception ignored) {}

                // 3. Patch LoadedApk (mPackageInfo)
                try {
                    Field mPackageInfo = baseContext.getClass().getDeclaredField("mPackageInfo");
                    mPackageInfo.setAccessible(true);
                    Object loadedApk = mPackageInfo.get(baseContext);

                    if (loadedApk != null) {
                        Field mClassLoader = loadedApk.getClass().getDeclaredField("mClassLoader");
                        mClassLoader.setAccessible(true);
                        mClassLoader.set(loadedApk, cl);

                        Field mLoadedApkResources = loadedApk.getClass().getDeclaredField("mResources");
                        mLoadedApkResources.setAccessible(true);
                        mLoadedApkResources.set(loadedApk, res);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "LoadedApk patch failed: " + e.getMessage());
                }

                // 4. 🔥 Inject mOuterContext (CRITICAL for View Attachment)
                try {
                    Field mOuterContext = baseContext.getClass().getDeclaredField("mOuterContext");
                    mOuterContext.setAccessible(true);
                    mOuterContext.set(baseContext, activity);
                } catch (Exception e) {
                    Logger.e(TAG, "mOuterContext injection failed: " + e.getMessage());
                }

                // 5. Fix LayoutInflater context
                try {
                    activity.getWindow().getLayoutInflater(); 
                    Field mContext = activity.getLayoutInflater().getClass().getDeclaredField("mContext");
                    mContext.setAccessible(true);
                    mContext.set(activity.getLayoutInflater(), activity);
                } catch (Exception ignored) {}
                
                Logger.i(TAG, "Injection SUCCEEDED for " + activity.getClass().getSimpleName());
            }

        } catch (Throwable e) {
            Logger.e(TAG, "Injection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Support for multiple execStartActivity signatures to ensure interception of all internal launches
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        intent = wrapIntentIfNecessary(who, intent);
        try {
            Method method = Instrumentation.class.getDeclaredMethod("execStartActivity", 
                Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class, int.class, Bundle.class);
            method.setAccessible(true);
            return (ActivityResult) method.invoke(base, who, contextThread, token, target, intent, requestCode, options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, String target,
            Intent intent, int requestCode, Bundle options) {
        
        intent = wrapIntentIfNecessary(who, intent);
        try {
            Method method = Instrumentation.class.getDeclaredMethod("execStartActivity", 
                Context.class, IBinder.class, IBinder.class, String.class, Intent.class, int.class, Bundle.class);
            method.setAccessible(true);
            return (ActivityResult) method.invoke(base, who, contextThread, token, target, intent, requestCode, options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Intent wrapIntentIfNecessary(Context who, Intent intent) {
        if (intent != null && intent.getComponent() != null) {
            String pkg = intent.getComponent().getPackageName();
            if (CloneManager.getInstance().getClonedPackage(pkg) != null) {
                Logger.d(TAG, "Intercepted internal launch: " + intent.getComponent().getClassName());
                Intent stubIntent = new Intent();
                stubIntent.setClassName(who.getPackageName(), "com.onecore.sdk.core.StubActivity");
                stubIntent.putExtra("target_package", pkg);
                stubIntent.putExtra("target_activity", intent.getComponent().getClassName());
                stubIntent.putExtra("_VA_TARGET_", new Intent(intent));
                stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return stubIntent;
            }
        }
        return intent;
    }

    @Override
    public android.app.Application newApplication(ClassLoader cl, String className, android.content.Context context) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        ClassLoader guestLoader = CloneManager.getInstance().getClassLoader();
        if (guestLoader != null) {
            Logger.i(TAG, "Virtual application mapping for: " + className);
            return base.newApplication(guestLoader, className, context);
        }
        
        return base.newApplication(cl, className, context);
    }

    @Override
    public void callApplicationOnCreate(android.app.Application app) {
        base.callApplicationOnCreate(app);
    }
}
