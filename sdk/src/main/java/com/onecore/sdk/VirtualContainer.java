package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import dalvik.system.DexClassLoader;
import android.content.res.AssetManager;
import android.content.res.Resources;
import java.lang.reflect.Method;

/**
 * Minimal Working Virtual Container Engine (Phase 2).
 * Handles APK Installation, Resources Injection, and Hooking.
 */
public class VirtualContainer {
    private static final String TAG = "VirtualContainer";
    private static VirtualContainer sInstance;
    
    private String mApkPath;
    private DexClassLoader mClassLoader;
    private String mPackageName;
    private Resources mResources;
    private android.app.Application mTargetApplication;
    private android.content.pm.ApplicationInfo mAppInfo;
    private java.util.Map<String, Integer> mActivityThemes = new java.util.HashMap<>();

    private VirtualContainer() {}

    public static synchronized VirtualContainer getInstance() {
        if (sInstance == null) {
            sInstance = new VirtualContainer();
        }
        return sInstance;
    }

    /**
     * Initializes the environment for a specific APK.
     */
    public boolean installApk(Context context, String apkPath, String packageName) {
        Log.i(TAG, "Installing APK for virtualization: " + apkPath);
        this.mApkPath = apkPath;
        this.mPackageName = packageName;

        try {
            android.content.pm.PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, 
                android.content.pm.PackageManager.GET_ACTIVITIES);
            if (packageInfo != null && packageInfo.applicationInfo != null) {
                mAppInfo = packageInfo.applicationInfo;
                mAppInfo.sourceDir = apkPath;
                mAppInfo.publicSourceDir = apkPath;
                
                // Register in VPM
                com.onecore.sdk.core.VPackageManager.registerPackage(packageInfo);
                
                if (packageInfo.activities != null) {
                    for (android.content.pm.ActivityInfo info : packageInfo.activities) {
                        mActivityThemes.put(info.name, info.theme != 0 ? info.theme : packageInfo.applicationInfo.theme);
                        Log.d(TAG, "Cached theme for " + info.name + ": " + info.theme);
                    }
                }
            }
            
            File dexOptDir = context.getDir("v_opt", Context.MODE_PRIVATE);
            File libDir = context.getDir("v_lib", Context.MODE_PRIVATE);

            // Extract native libs
            com.onecore.sdk.core.NativeLibExtractor.extract(apkPath, libDir);

            // 1. Setup ClassLoader
            mClassLoader = new DexClassLoader(
                apkPath,
                dexOptDir.getAbsolutePath(),
                libDir.getAbsolutePath(),
                context.getClassLoader()
            );
            
            // 2. Setup Resources for the target APK
            setupResources(context, apkPath);
            
            // 3. Inject LoadedApk into ActivityThread
            com.onecore.sdk.core.LoadedApkManager.inject(context, apkPath, packageName, mClassLoader, mResources);
            
            Log.i(TAG, "OneCore-DEBUG: LoadedApk injected");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize environment", e);
            return false;
        }
    }

    private void setupResources(Context context, String apkPath) {
        try {
            // More robust resource loading
            mResources = context.getPackageManager().getResourcesForApplication(mAppInfo);
            Log.i(TAG, "Resources injected via PackageManager for: " + mPackageName);
        } catch (Exception e) {
            Log.e(TAG, "PackageManager Resources failed, falling back to manual AssetManager", e);
            try {
                AssetManager assetManager = AssetManager.class.newInstance();
                Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
                addAssetPath.invoke(assetManager, apkPath);
                
                Resources hostRes = context.getResources();
                mResources = new Resources(assetManager, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
                Log.i(TAG, "Manual Resources fallback successful.");
            } catch (Exception e2) {
                Log.e(TAG, "Manual Resources fallback FAILED", e2);
            }
        }
    }

    /**
     * Launches the target activity via system intent.
        if (mClassLoader == null) {
            Log.e(TAG, "Cannot launch: ClassLoader not initialized. Call installApk first.");
            return;
        }

        Log.i(TAG, ">>> V_CORE: Launching Virtual Activity: " + targetActivity);
        
        try {
            // We use a pure system Intent launch. 
            // Our Instrumentation.execStartActivity MUST intercept this.
            Intent intent = new Intent();
            intent.setComponent(new android.content.ComponentName(mPackageName, targetActivity));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            Log.d(TAG, "Dispatching system intent for virtualization...");
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Virtual Launch Dispatch Failed. This is expected if target is not in manifest AND hooks failed.", e);
        }
    }

    /**
     * Loads and initializes the target application instance.
     */
    public void bindApplication(Context context, String applicationClassName, String packageName) {
        if (mClassLoader == null) return;
        
        try {
            Log.i(TAG, ">>> V_CORE: Binding Target Application [" + applicationClassName + "] <<<");
            
            Class<?> appClass = mClassLoader.loadClass(applicationClassName);
            mTargetApplication = (android.app.Application) appClass.newInstance();
            
            // Fix context before attaching
            com.onecore.sdk.core.ContextFixer.fixContext(context, packageName);

            Method attach = android.app.Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            attach.invoke(mTargetApplication, context);
            
            mTargetApplication.onCreate();
            Log.i(TAG, ">>> V_CORE: Application Bound and Active. <<<");
        } catch (Exception e) {
            Log.e(TAG, "!!! V_CORE: Application Binding FAILED !!!", e);
        }
    }

    public android.app.Application getTargetApplication() {
        return mTargetApplication;
    }

    public String getApkPath() {
        return mApkPath;
    }

    public Integer getTheme(String activityClassName) {
        return mActivityThemes.get(activityClassName);
    }

    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    public Resources getResources() {
        return mResources;
    }
}
