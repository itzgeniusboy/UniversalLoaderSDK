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
        Log.i(TAG, "OneCoreInstrumentation initialized with base: " + (base != null ? base.getClass().getName() : "null"));
    }

    /**
     * Hidden method in Instrumentation. We override it to intercept intent launches.
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        Log.d(TAG, "execStartActivity intercepted for: " + intent);
        
        String targetPackage = intent.getComponent() != null ? intent.getComponent().getPackageName() : null;
        String targetClass = intent.getComponent() != null ? intent.getComponent().getClassName() : null;
        
        // Redirect to StubActivity if it's an external (virtualized) activity
        if (targetClass != null && !targetClass.startsWith(who.getPackageName())) {
            Log.i(TAG, "Redirecting launch for external activity: " + targetClass);
            
            Intent stubIntent = new Intent();
            stubIntent.setClassName(who.getPackageName(), "com.onecore.loader.StubActivity");
            stubIntent.putExtra("target_activity", targetClass);
            stubIntent.putExtra("target_package", targetPackage);
            if (intent.getExtras() != null) {
                stubIntent.putExtras(intent.getExtras());
            }
            stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            intent = stubIntent;
        }

        try {
            Method execMethod = Instrumentation.class.getDeclaredMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class);
            execMethod.setAccessible(true);
            return (ActivityResult) execMethod.invoke(mBase, who, contextThread, token, target, intent, requestCode, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to invoke base execStartActivity", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        String targetActivity = intent.getStringExtra("target_activity");
        Log.d(TAG, "newActivity called for: " + className + ", target_activity extra: " + targetActivity);

        if (targetActivity != null) {
            Log.i(TAG, "!!! INTERCEPT SUCCESS !!! Creating target instance: " + targetActivity);
            ClassLoader virtualCl = VirtualContainer.getInstance().getClassLoader();
            
            Log.d(TAG, "Using ClassLoader: " + (virtualCl != null ? "VIRTUAL" : "HOST"));
            
            if (virtualCl != null) {
                return mBase.newActivity(virtualCl, targetActivity, intent);
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
