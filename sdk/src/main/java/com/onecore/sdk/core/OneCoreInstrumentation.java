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
 * Custom Instrumentation to intercept Activity lifecycle and fix ClassLoaders/Context.
 */
public class OneCoreInstrumentation extends Instrumentation {
    private static final String TAG = "OneCoreInstrumentation";
    private final Instrumentation mBase;

    public OneCoreInstrumentation(Instrumentation base) {
        this.mBase = base;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        String targetActivity = intent.getStringExtra("target_activity");
        if (targetActivity != null) {
            Log.i(TAG, "Intercepting newActivity for target: " + targetActivity);
            ClassLoader virtualCl = VirtualContainer.getInstance().getClassLoader();
            if (virtualCl != null) {
                return mBase.newActivity(virtualCl, targetActivity, intent);
            }
        }
        
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        if (activity.getIntent().hasExtra("target_activity")) {
            fixActivityContext(activity);
        }
        mBase.callActivityOnCreate(activity, icicle);
    }

    private void fixActivityContext(Activity activity) {
        try {
            Log.i(TAG, "Fixing context for activity: " + activity.getClass().getName());
            
            // 0. Patch Package names in ContextImpl
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
                    
                    Log.d(TAG, "Fixed ContextImpl PackageNames to: " + targetPkg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fix PackageNames in ContextImpl", e);
            }

            // 1. Patch Resources
            try {
                Field mResourcesField = baseContext.getClass().getDeclaredField("mResources");
                mResourcesField.setAccessible(true);
                mResourcesField.set(baseContext, container.getResources());
                Log.d(TAG, "Fixed ContextImpl Resources");
            } catch (Exception e) {
                Log.e(TAG, "Failed to fix ContextImpl Resources", e);
            }

            // 2. Patch LoadedApk (mPackageInfo)
            try {
                Field mPackageInfoField = baseContext.getClass().getDeclaredField("mPackageInfo");
                mPackageInfoField.setAccessible(true);
                Object mPackageInfo = mPackageInfoField.get(baseContext);

                // mClassLoader
                Field mClassLoaderField = mPackageInfo.getClass().getDeclaredField("mClassLoader");
                mClassLoaderField.setAccessible(true);
                mClassLoaderField.set(mPackageInfo, container.getClassLoader());

                // mResources
                Field mLoadedApkResField = mPackageInfo.getClass().getDeclaredField("mResources");
                mLoadedApkResField.setAccessible(true);
                mLoadedApkResField.set(mPackageInfo, container.getResources());
                
                Log.d(TAG, "Fixed LoadedApk ClassLoader and Resources");
            } catch (Exception e) {
                Log.e(TAG, "Failed to fix LoadedApk", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in fixActivityContext", e);
        }
    }
}
