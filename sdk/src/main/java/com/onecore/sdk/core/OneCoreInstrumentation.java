package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Advanced Instrumentation to intercept Activity lifecycle and fix ClassLoaders.
 */
public class OneCoreInstrumentation extends Instrumentation {
    private static final String TAG = "OneCoreInstrumentation";
    private final Instrumentation mBase;

    public OneCoreInstrumentation(Instrumentation base) {
        this.mBase = base;
        Log.i(TAG, ">>> OneCore: Custom Instrumentation Installed (base=" + (base != null ? base.getClass().getName() : "null") + ") <<<");
    }

    /**
     * Hidden method in Instrumentation. Intercepts startActivity calls.
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        Log.d(TAG, ">>> CALL: execStartActivity from " + who.getClass().getSimpleName() + " for " + intent);
        
        String targetPackage = intent.getComponent() != null ? intent.getComponent().getPackageName() : null;
        String targetClass = intent.getComponent() != null ? intent.getComponent().getClassName() : null;
        
        // If the activity is from a virtualized package, redirect it to our StubActivity
        if (targetClass != null && !targetClass.startsWith(who.getPackageName())) {
            Log.i(TAG, ">>> REDIRECT: Outgoing Activity [" + targetClass + "] detected. Re-routing through StubActivity.");
            
            Intent stubIntent = new Intent();
            stubIntent.setClassName(who.getPackageName(), "com.onecore.loader.StubActivity");
            stubIntent.putExtra("target_activity", targetClass);
            stubIntent.putExtra("target_package", targetPackage);
            
            if (intent.getExtras() != null) {
                stubIntent.putExtras(intent.getExtras());
            }
            stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            intent = stubIntent;
            Log.d(TAG, ">>> REDIRECT SUCCESS: New Intent = " + intent);
        }

        try {
            Method execMethod = Instrumentation.class.getDeclaredMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class);
            execMethod.setAccessible(true);
            return (ActivityResult) execMethod.invoke(mBase, who, contextThread, token, target, intent, requestCode, options);
        } catch (Exception e) {
            Log.e(TAG, "!!! ERROR: execStartActivity redirection FAILED !!!", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        Log.d(TAG, ">>> CALL: newActivity(className=" + className + ") <<<");
        
        String targetActivity = intent.getStringExtra("target_activity");
        if (targetActivity != null) {
            Log.i(TAG, ">>> INTERCEPT: Creating instance for Target Activity [" + targetActivity + "] <<<");
            ClassLoader virtualCl = VirtualContainer.getInstance().getClassLoader();
            
            if (virtualCl != null) {
                Log.d(TAG, ">>> USING VIRTUAL ClassLoader for instantiation.");
                return mBase.newActivity(virtualCl, targetActivity, intent);
            } else {
                Log.e(TAG, ">>> CRITICAL: Virtual ClassLoader is NULL! Falling back to base loader.");
            }
        }
        
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Log.d(TAG, "callActivityOnCreate: " + activity.getClass().getName());
        if (activity.getIntent().hasExtra("target_activity")) {
            fixActivityContext(activity);
        }
        mBase.callActivityOnCreate(activity, icicle);
    }

    private void fixActivityContext(Activity activity) {
        try {
            Log.i(TAG, "Patching Activity Context: " + activity.getClass().getName());
            Context baseContext = activity.getBaseContext();
            VirtualContainer container = VirtualContainer.getInstance();
            
            // Fix Package Names
            try {
                String targetPkg = activity.getIntent().getStringExtra("target_package");
                if (targetPkg != null) {
                    Field mPackageNameField = baseContext.getClass().getDeclaredField("mPackageName");
                    mPackageNameField.setAccessible(true);
                    mPackageNameField.set(baseContext, targetPkg);

                    try {
                        Field mBasePackageNameField = baseContext.getClass().getDeclaredField("mBasePackageName");
                        mBasePackageNameField.setAccessible(true);
                        mBasePackageNameField.set(baseContext, targetPkg);
                    } catch (NoSuchFieldException ignored) {}
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fix PackageNames", e);
            }

            // Fix Resources
            if (container.getResources() != null) {
                try {
                    Field mResourcesField = baseContext.getClass().getDeclaredField("mResources");
                    mResourcesField.setAccessible(true);
                    mResourcesField.set(baseContext, container.getResources());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fix Resources", e);
                }
            }

            // Fix ClassLoader in LoadedApk
            try {
                Field mPackageInfoField = baseContext.getClass().getDeclaredField("mPackageInfo");
                mPackageInfoField.setAccessible(true);
                Object mPackageInfo = mPackageInfoField.get(baseContext);

                if (mPackageInfo != null) {
                    Field mClassLoaderField = mPackageInfo.getClass().getDeclaredField("mClassLoader");
                    mClassLoaderField.setAccessible(true);
                    mClassLoaderField.set(mPackageInfo, container.getClassLoader());

                    if (container.getResources() != null) {
                        Field mLoadedApkResField = mPackageInfo.getClass().getDeclaredField("mResources");
                        mLoadedApkResField.setAccessible(true);
                        mLoadedApkResField.set(mPackageInfo, container.getResources());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fix LoadedApk", e);
            }

            Log.i(TAG, "Context patching COMPLETE.");
        } catch (Exception e) {
            Log.e(TAG, "Context patching FAILED", e);
        }
    }
}
