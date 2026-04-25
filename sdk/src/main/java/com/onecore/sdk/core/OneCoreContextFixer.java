package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.core.reflex.ReflectionHelper;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * Version-adaptive utility to fix ContextImpl and LoadedApk fields.
 */
public class OneCoreContextFixer {
    private static final String TAG = "OneCore-ContextFixer";

    public static void fixContext(Context context, String packageName) {
        if (context == null) return;
        
        SafeExecutionManager.run("Context Fix (" + packageName + ")", () -> {
            // Find the ContextImpl instance
            Context baseContext = context;
            while (baseContext instanceof android.content.ContextWrapper) {
                baseContext = ((android.content.ContextWrapper) baseContext).getBaseContext();
            }

            final Object contextImpl = baseContext;
            
            // 1. Fix Package Names and UID dynamically
            ReflectionHelper.setFieldValue(contextImpl, packageName, "mPackageName", "mBasePackageName", "mOpPackageName");
            
            // Fix Storage Paths
            File dataDir = context.getDir("v_data_" + packageName, Context.MODE_PRIVATE);
            if (!dataDir.exists()) dataDir.mkdirs();
            
            // Initialize VFS for this package
            OneCoreVFS.init(packageName, dataDir.getAbsolutePath());
            
            ReflectionHelper.setFieldValue(contextImpl, dataDir, "mDatadir", "mDataDir");
                
            File filesDir = new File(dataDir, "files");
            if (!filesDir.exists()) filesDir.mkdirs();
            ReflectionHelper.setFieldValue(contextImpl, filesDir, "mFilesDir");
                
            File cacheDir = new File(dataDir, "cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            ReflectionHelper.setFieldValue(contextImpl, cacheDir, "mCacheDir");
                
            // 2. Fix Resources & ClassLoader & LayoutInflater
            Resources virtualRes = VirtualContainer.getInstance().getResources();
            ClassLoader virtualCl = VirtualContainer.getInstance().getClassLoader();
            
            if (virtualCl != null) {
                ReflectionHelper.setFieldValue(contextImpl, virtualCl, "mClassLoader");
            }

            if (virtualRes != null) {
                ReflectionHelper.setFieldValue(contextImpl, virtualRes, "mResources");
                
                // Fix AssetManager specifically for Android 10+
                try {
                    Object assetManager = virtualRes.getAssets();
                    ReflectionHelper.setFieldValue(contextImpl, assetManager, "mAssets");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to inject AssetManager specifically");
                }
                
                // CRITICAL: Clone LayoutInflater and inject back
                android.view.LayoutInflater original = (android.view.LayoutInflater) baseContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (original != null && !(original.getClass().getName().contains("OneCore"))) {
                    android.view.LayoutInflater virtualInflater = original.cloneInContext(context);
                    ReflectionHelper.setFieldValue(contextImpl, virtualInflater, "mInflater");
                }
            }

            // 3. Fix mPackageInfo (LoadedApk) safely across versions
            injectLoadedApk(contextImpl, packageName);

            // 4. Activity specific fixes
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                ReflectionHelper.setFieldValue(activity, virtualRes, "mResources");
                
                // Reset theme to force reload from virtual resources
                try {
                    ReflectionHelper.setFieldValue(activity, 0, "mThemeResource");
                    ReflectionHelper.setFieldValue(activity, null, "mTheme");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to reset theme cache");
                }

                ReflectionHelper.setFieldValue(context, null, "mInflater");
            }

            // 5. Shared Storage Fixing
            OneCoreStorageFix.fix(context, packageName);
            
            Log.i(TAG, "OneCore-DEBUG: Virtual Context successfully patched for " + packageName);
        });
    }

    private static void injectLoadedApk(Object contextImpl, String packageName) {
        SafeExecutionManager.run("LoadedApk Injection", () -> {
            Object activityThread = ReflectionHelper.invokeMethod(null, "currentActivityThread");
            if (activityThread == null) return;

            Map<String, WeakReference<Object>> mPackages = (Map) ReflectionHelper.getFieldValue(activityThread, "mPackages");
            if (mPackages != null) {
                WeakReference<Object> ref = mPackages.get(packageName);
                if (ref != null) {
                    Object loadedApk = ref.get();
                    if (loadedApk != null) {
                        ReflectionHelper.setFieldValue(contextImpl, loadedApk, "mPackageInfo");
                        
                        // Fix Application link in LoadedApk
                        android.app.Application virtualApp = VirtualContainer.getInstance().getTargetApplication();
                        if (virtualApp != null) {
                            ReflectionHelper.setFieldValue(loadedApk, virtualApp, "mApplication");
                            ReflectionHelper.setFieldValue(contextImpl, virtualApp, "mApplication");
                        }
                    }
                }
            }
        });
    }
}
