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
        
        File apkFile = new File(apkPath);
        if (!apkFile.exists()) {
            Log.e(TAG, "Target APK not found at: " + apkPath);
            return false;
        }

        this.mApkPath = apkPath;
        this.mPackageName = packageName;

        try {
            android.content.pm.PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, 
                android.content.pm.PackageManager.GET_ACTIVITIES | android.content.pm.PackageManager.GET_PROVIDERS | android.content.pm.PackageManager.GET_RECEIVERS);
            if (packageInfo != null && packageInfo.applicationInfo != null) {
                mAppInfo = packageInfo.applicationInfo;
                mAppInfo.sourceDir = apkPath;
                mAppInfo.publicSourceDir = apkPath;
                
                // Ensure native lib dir is set correctly
                File libDir = context.getDir("v_lib_" + packageName, Context.MODE_PRIVATE);
                mAppInfo.nativeLibraryDir = libDir.getAbsolutePath();
                
                // Register in VPM
                com.onecore.sdk.core.OneCorePackageManagerProxy.registerPackage(packageInfo);
                
                if (packageInfo.activities != null) {
                    for (android.content.pm.ActivityInfo info : packageInfo.activities) {
                        mActivityThemes.put(info.name, info.theme != 0 ? info.theme : packageInfo.applicationInfo.theme);
                    }
                }
            } else {
                Log.e(TAG, "Failed to parse APK: " + apkPath);
                return false;
            }
            
            // 0. Hidden API Bypass
            com.onecore.sdk.core.OneCoreHiddenApiFixer.bypass();

            File dexOptDir = context.getDir("v_opt_" + packageName, Context.MODE_PRIVATE);

            // 1. Extract native libs
            String libPath = com.onecore.sdk.core.OneCoreNativeLoader.copyNativeBinaries(context, apkPath, packageName);

            // 2. Setup ClassLoader
            mClassLoader = new DexClassLoader(
                apkPath,
                dexOptDir.getAbsolutePath(),
                libPath,
                context.getClassLoader()
            );
            
            // 2.1 Fix OBB for Games
            fixGameObb(packageName);
            
            // 2.2 Setup Resources for the target APK
            setupResources(context, apkPath);
            
            // 3. Inject LoadedApk into ActivityThread
            com.onecore.sdk.core.OneCoreLoadedApkManager.getLoadedApk(context, apkPath, packageName, mClassLoader, mResources);
            
            // 4. Register Receivers
            com.onecore.sdk.core.OneCoreBroadcastManager.registerReceivers(context, packageInfo);
            
            // 5. Install Content Providers
            com.onecore.sdk.core.OneCoreContentProviderManager.installProviders(context, packageInfo.providers);
            
            Log.i(TAG, "OneCore-DEBUG: Virtual Space Ready for " + packageName);
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
                AssetManager assetManager = (AssetManager) AssetManager.class.newInstance();
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
     * Launches the target activity via system intent redirected through stubs.
     */
    public void launch(Context context, String targetActivity) {
        if (mClassLoader == null) {
            Log.e(TAG, "Cannot launch: ClassLoader not initialized. Call installApk first.");
            return;
        }

        Log.i(TAG, ">>> V_CORE: Launching Virtual Activity: " + targetActivity);
        
        try {
            // We target our own host package and a stub activity to ensure correct system redirection.
            // OneCoreInstrumentation will intercept this call and handle the virtual component swap.
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), "com.onecore.loader.StubActivity_P1");
            intent.putExtra("target_activity", targetActivity);
            intent.putExtra("target_package", mPackageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            Log.d(TAG, "Dispatching stub intent for virtualization start...");
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Virtual Launch Dispatch Failed.", e);
        }
    }

    /**
     * Loads and initializes the target application instance.
     */
    public void bindApplication(Context context, String applicationClassName, String packageName) {
        if (mClassLoader == null || mTargetApplication != null) return;
        
        try {
            Log.i(TAG, ">>> V_CORE: Binding Target Application [" + applicationClassName + "] <<<");
            
            // 1. Fix base context first
            com.onecore.sdk.core.OneCoreContextFixer.fixContext(context, packageName);
            
            // 2. Create instance
            Class<?> appClass = mClassLoader.loadClass(applicationClassName);
            mTargetApplication = (android.app.Application) appClass.newInstance();
            
            // 3. Attach base context
            Method attach = android.app.Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            attach.invoke(mTargetApplication, context);
            
            // 4. Call onCreate
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

    public android.content.pm.ApplicationInfo getAppInfo() {
        return mAppInfo;
    }

    private void fixGameObb(String packageName) {
        try {
            File obbDir = new File(android.os.Environment.getExternalStorageDirectory(), "Android/obb/" + packageName);
            if (!obbDir.exists()) {
                Log.w(TAG, "OneCore-DEBUG: OBB directory missing. BGMI/PUBG might not start. Path: " + obbDir.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "OBB Check Failed", e);
        }
    }
}
