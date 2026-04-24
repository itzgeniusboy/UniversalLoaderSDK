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
        
        Intent target = intent.getParcelableExtra("_VA_TARGET_");
        if (target != null && target.getComponent() != null) {
            String targetActivity = target.getComponent().getClassName();
            try {
                ClassLoader appCl = CloneManager.getInstance().getClassLoader();
                Logger.i("VA", "Instrumentation redirecting to: " + targetActivity);
                
                // Ensure target remains set to the correct class
                target.setExtrasClassLoader(appCl);
                
                return super.newActivity(appCl, targetActivity, target);
            } catch (Throwable e) {
                Logger.e(TAG, "newActivity redirection failed: " + e.getMessage());
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
                        
                        // Patch Application instance inside LoadedApk if it exists
                        Field mApplication = loadedApk.getClass().getDeclaredField("mApplication");
                        mApplication.setAccessible(true);
                        Object app = mApplication.get(loadedApk);
                        if (app == null) {
                            // If app is null, we might need to set the one we created
                            // But usually makeApplication handles this.
                        }
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
                stubIntent.putExtra("_VA_TARGET_", new Intent(intent));
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
