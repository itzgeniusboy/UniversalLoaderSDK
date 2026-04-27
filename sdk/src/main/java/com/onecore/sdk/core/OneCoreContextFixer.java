package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.ReflectionHelper;
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
        
        Log.i(TAG, "OneCore-Context: FIX INITIATED for " + context.getClass().getName() + " [" + packageName + "]");

        SafeExecutionManager.run("Context Fix (" + packageName + ")", () -> {
            // Find the ContextImpl instance
            Context baseContext = context;
            while (baseContext instanceof android.content.ContextWrapper) {
                baseContext = ((android.content.ContextWrapper) baseContext).getBaseContext();
            }

            final Object contextImpl = baseContext;
            Log.d(TAG, "OneCore-Context: ContextImpl instance found -> " + contextImpl);
            
            // 1. Fix Package Names and UID dynamically
            String[] fields = {"mPackageName", "mBasePackageName", "mOpPackageName", "mAttributionTag", "mAttributionSource"};
            for (String field : fields) {
                try {
                    ReflectionHelper.setFieldValue(contextImpl, packageName, field);
                } catch (Exception ignored) {}
            }
            
            // Fix AttributionSource (Android 12+)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    Object attributionSource = ReflectionHelper.getFieldValue(contextImpl, "mAttributionSource");
                    if (attributionSource != null) {
                        ReflectionHelper.setFieldValue(attributionSource, packageName, "mPackageName");
                        Log.d(TAG, "OneCore-Context: AttributionSource patched.");
                    }
                } catch (Exception ignored) {}
            }
            
            // Fix PackageName in ContextWrapper (e.g. Application, Activity)
            if (context instanceof android.content.ContextWrapper) {
                 try {
                     ReflectionHelper.setFieldValue(context, packageName, "mBasePackageName", "mOpPackageName");
                 } catch (Exception ignored) {}
            }
            
            // Fix Storage Paths
            File dataDir = context.getDir("v_data_" + packageName, Context.MODE_PRIVATE);
            if (!dataDir.exists()) dataDir.mkdirs();
            Log.d(TAG, "OneCore-Context: Storage root set to -> " + dataDir.getAbsolutePath());
            
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
                Log.d(TAG, "OneCore-Context: ClassLoader swapped.");
            }

            if (virtualRes != null) {
                ReflectionHelper.setFieldValue(contextImpl, virtualRes, "mResources");
                Log.d(TAG, "OneCore-Context: Resources swapped.");
                
                // Fix AssetManager specifically for Android 10+
                try {
                    android.content.res.AssetManager assetManager = virtualRes.getAssets();
                    ReflectionHelper.setFieldValue(contextImpl, assetManager, "mAssets");
                    // CRITICAL: Ensure the APK path is in the AssetManager used by this context
                    ReflectionHelper.invokeMethod(assetManager, "addAssetPath", VirtualContainer.getInstance().getApkPath());
                    Log.d(TAG, "OneCore-Context: AssetManager path verified.");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to inject or update AssetManager");
                }
                
                // CRITICAL: Clone LayoutInflater and inject back
                android.view.LayoutInflater original = (android.view.LayoutInflater) baseContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (original != null) {
                    android.view.LayoutInflater virtualInflater = original.cloneInContext(context);
                    ReflectionHelper.setFieldValue(contextImpl, virtualInflater, "mInflater");
                    Log.d(TAG, "OneCore-Context: LayoutInflater cloned and injected");
                }
            }

            // 3. Fix mPackageInfo (LoadedApk) safely across versions
            injectLoadedApk(contextImpl, packageName);

            // Additional fix for Application object link in ContextImpl
            android.app.Application virtualApp = VirtualContainer.getInstance().getTargetApplication();
            if (virtualApp != null) {
                ReflectionHelper.setFieldValue(contextImpl, virtualApp, "mInitialApplication", "mApplication");
                Log.d(TAG, "OneCore-Context: Application object linked.");
            }

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

                // Inject virtual inflater directly into Activity and Window
                android.view.LayoutInflater activityInflater = activity.getLayoutInflater().cloneInContext(activity);
                ReflectionHelper.setFieldValue(activity, activityInflater, "mInflater");
                
                if (activity.getWindow() != null) {
                    ReflectionHelper.setFieldValue(activity.getWindow(), activityInflater, "mLayoutInflater");
                    // Ensure window is not transparent by default
                    activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF000000)); 
                    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }
                
                Log.d(TAG, "OneCore-Context: Activity Pipeline Patched.");
            }

            // Fix Display (Android 12+ Fix)
            try {
                android.view.Display display = null;
                if (context instanceof Activity) {
                    display = ((Activity) context).getWindowManager().getDefaultDisplay();
                } else {
                    android.view.WindowManager wm = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) display = wm.getDefaultDisplay();
                }
                if (display != null) {
                    ReflectionHelper.setFieldValue(contextImpl, display, "mDisplay");
                    Log.d(TAG, "OneCore-Context: mDisplay fixed.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to fix mDisplay field");
            }

            // 5. Shared Storage Fixing
            OneCoreStorageFix.fix(context, packageName);
            
            Log.i(TAG, "OneCore-Context: FIX SUCCESSFUL for " + packageName);
        });
    }

    private static void injectLoadedApk(Object contextImpl, String packageName) {
        SafeExecutionManager.run("LoadedApk Injection", () -> {
            Object activityThread = ReflectionHelper.invokeMethod("android.app.ActivityThread", "currentActivityThread");
            if (activityThread == null) return;

            Map<String, WeakReference<Object>> mPackages = (Map) ReflectionHelper.getFieldValue(activityThread, "mPackages");
            if (mPackages != null) {
                WeakReference<Object> ref = mPackages.get(packageName);
                if (ref != null) {
                    Object loadedApk = ref.get();
                    if (loadedApk != null) {
                        ReflectionHelper.setFieldValue(contextImpl, loadedApk, "mPackageInfo");
                        
                        // Fix ApplicationInfo within LoadedApk
                        android.content.pm.ApplicationInfo virtualInfo = VirtualContainer.getInstance().getAppInfo();
                        if (virtualInfo != null) {
                            ReflectionHelper.setFieldValue(loadedApk, virtualInfo, "mApplicationInfo");
                        }

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
