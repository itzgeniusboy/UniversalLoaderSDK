package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.onecore.sdk.utils.Logger;

/**
 * Custom Instrumentation to intercept Activity life-cycle and launch events.
 * Resolves the "Black Screen" issue by properly mapping ClassLoaders and Resources.
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
        
        Logger.d(TAG, "newActivity intercept: " + className);
        
        // 1. Check if this is a redirection launch from our StubActivity
        Intent target = intent.getParcelableExtra("_VA_TARGET_");
        if (target != null && target.getComponent() != null) {
            String targetActivity = target.getComponent().getClassName();
            Logger.i(TAG, "Redirection [Stub -> Real]: " + targetActivity);
            
            // 2. Use the Guest ClassLoader to load the real Activity
            cl = CloneManager.getInstance().getClassLoader();
            className = targetActivity;
            intent = target;
        }

        return super.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Logger.i(TAG, ">>> callActivityOnCreate: " + activity.getClass().getName());
        
        // 3. Critically inject Resources and ClassLoader before Activity.onCreate is called
        injectGuestContext(activity);
        
        try {
            mBase.callActivityOnCreate(activity, icicle);
        } catch (Throwable e) {
            Logger.e(TAG, "callActivityOnCreate Hook failed for " + activity.getClass().getName(), e);
            throw e;
        }
    }

    private void injectGuestContext(Activity activity) {
        try {
            Context baseContext = activity.getBaseContext();
            android.content.res.Resources guestRes = CloneManager.getInstance().getResources();
            ClassLoader guestLoader = CloneManager.getInstance().getClassLoader();

            if (guestRes != null) {
                Logger.d(TAG, "Injecting Guest Resources into: " + activity.getClass().getSimpleName());

                // Patch ContextImpl
                try {
                    Field mResources = baseContext.getClass().getDeclaredField("mResources");
                    mResources.setAccessible(true);
                    mResources.set(baseContext, guestRes);
                    
                    Field mTheme = baseContext.getClass().getDeclaredField("mTheme");
                    mTheme.setAccessible(true);
                    mTheme.set(baseContext, null); // Reset theme for re-inflation
                } catch (Exception e) {}

                // Patch Activity Resource cache
                try {
                    Field mActivityRes = Activity.class.getDeclaredField("mResources");
                    mActivityRes.setAccessible(true);
                    mActivityRes.set(activity, guestRes);
                } catch (Exception e) {}

                // Patch LoadedApk (The most critical part for View rendering)
                try {
                    Field mPackageInfo = baseContext.getClass().getDeclaredField("mPackageInfo");
                    mPackageInfo.setAccessible(true);
                    Object loadedApk = mPackageInfo.get(baseContext);

                    if (loadedApk != null) {
                        Field mClassLoader = loadedApk.getClass().getDeclaredField("mClassLoader");
                        mClassLoader.setAccessible(true);
                        mClassLoader.set(loadedApk, guestLoader);

                        Field mLoadedApkResources = loadedApk.getClass().getDeclaredField("mResources");
                        mLoadedApkResources.setAccessible(true);
                        mLoadedApkResources.set(loadedApk, guestRes);
                    }
                } catch (Exception e) {}
                
                Logger.i(TAG, "Guest Context successfully injected.");
            }
        } catch (Throwable e) {
            Logger.e(TAG, "Guest Context Injection FAILED", e);
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
                Logger.d(TAG, "Intercepted internal launch request: " + intent.getComponent().getClassName());
                
                Intent stub = new Intent();
                stub.setClassName(who.getPackageName(), "com.onecore.sdk.core.StubActivity");
                stub.putExtra("_VA_TARGET_", new Intent(intent));
                stub.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return stub;
            }
        }
        return intent;
    }
}
