package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import java.lang.reflect.Field;
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
        
        String targetActivity = intent.getStringExtra("target_activity");

        if (targetActivity != null) {
            try {
                ClassLoader appCl = CloneManager.getInstance().getClassLoader();
                Logger.i("VA", "Redirecting to: " + targetActivity);

                // Restore original intent for the guest activity if it exists
                Intent originalIntent = intent.getParcelableExtra("original_intent");
                if (originalIntent != null) {
                    intent.fillIn(originalIntent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_ACTION | Intent.FILL_IN_DATA | Intent.FILL_IN_CATEGORIES);
                }
                
                // Cleanup virtual platform extras
                intent.removeExtra("target_activity");
                intent.removeExtra("target_package");
                intent.removeExtra("original_intent");
                
                // Ensure correct component is set
                String targetPackage = intent.getComponent() != null ? intent.getComponent().getPackageName() : null;
                if (targetPackage == null) {
                    targetPackage = com.onecore.sdk.OneCoreSDK.getContext().getPackageName();
                }
                intent.setClassName(targetPackage, targetActivity);

                // Revert to super.newActivity as H Hook handles intent fixing
                return super.newActivity(appCl, targetActivity, intent);

            } catch (Throwable e) {
                Logger.e(TAG, "Redirection failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return super.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Logger.i(TAG, ">>> HOOK ACTIVE: callActivityOnCreate for " + activity.getClass().getName());
        injectContext(activity);
        base.callActivityOnCreate(activity, icicle);
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

    // 🔥 CRITICAL: Context Fix
    private void injectContext(Activity activity) {
        try {
            Context baseContext = activity.getBaseContext();
            Resources res = CloneManager.getInstance().getResources();
            ClassLoader cl = CloneManager.getInstance().getClassLoader();

            if (res != null) {
                // Replace Resources
                Field mResources = Context.class.getDeclaredField("mResources");
                mResources.setAccessible(true);
                mResources.set(baseContext, res);
                
                // Also set it on the activity itself
                try {
                    Field mActivityResources = Activity.class.getDeclaredField("mResources");
                    mActivityResources.setAccessible(true);
                    mActivityResources.set(activity, res);
                } catch (Exception ignored) {}

                // Replace ClassLoader
                try {
                    Field mPackageInfo = Context.class.getDeclaredField("mPackageInfo");
                    mPackageInfo.setAccessible(true);
                    Object loadedApk = mPackageInfo.get(baseContext);

                    Field mClassLoader = loadedApk.getClass().getDeclaredField("mClassLoader");
                    mClassLoader.setAccessible(true);
                    mClassLoader.set(loadedApk, cl);

                    // Sync resources to LoadedApk as well
                    try {
                        Field mLoadedApkResources = loadedApk.getClass().getDeclaredField("mResources");
                        mLoadedApkResources.setAccessible(true);
                        mLoadedApkResources.set(loadedApk, res);
                    } catch (Exception ignored) {}

                } catch (Exception e) {
                    Logger.e(TAG, "Failed to inject into LoadedApk: " + e.getMessage());
                }

                // 🔥 Inject mOuterContext so views attach correctly
                try {
                    Field mOuterContext = baseContext.getClass().getDeclaredField("mOuterContext");
                    mOuterContext.setAccessible(true);
                    mOuterContext.set(baseContext, activity);
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to inject mOuterContext: " + e.getMessage());
                }

                // Inject into LayoutInflater
                try {
                    activity.getWindow().getLayoutInflater(); // Trigger creation
                    Field mContext = activity.getLayoutInflater().getClass().getDeclaredField("mContext");
                    mContext.setAccessible(true);
                    mContext.set(activity.getLayoutInflater(), activity);
                } catch (Exception ignored) {}
            }

        } catch (Throwable e) {
            Logger.e(TAG, "Injection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Intercept Activity launch at the client side.
     * This is a hidden method in Instrumentation.
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        Logger.i(TAG, "execStartActivity Hooked. Target: " + (intent.getComponent() != null ? intent.getComponent().getClassName() : intent.toString()));

        // Check if the target belongs to the guest app
        if (intent.getComponent() != null) {
            String packageName = intent.getComponent().getPackageName();
            String className = intent.getComponent().getClassName();
            
            // If it's not our own package, it's likely a guest activity (or a system one)
            // For now, if we are in guest mode, we redirect.
            if (CloneManager.getInstance().getClonedPackage(packageName) != null) {
                Logger.d(TAG, "Redirecting internal guest activity launch: " + className);
                
                Intent stubIntent = new Intent();
                stubIntent.setClassName(who.getPackageName(), "com.onecore.sdk.core.StubActivity");
                stubIntent.putExtra("target_activity", className);
                stubIntent.putExtra("target_package", packageName);
                stubIntent.putExtra("original_intent", new Intent(intent));
                stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                intent = stubIntent;
            }
        }
        
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
