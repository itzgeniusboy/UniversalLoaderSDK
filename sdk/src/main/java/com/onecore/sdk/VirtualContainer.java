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

        File dexOptDir = context.getDir("v_opt", Context.MODE_PRIVATE);
        File libDir = context.getDir("v_lib", Context.MODE_PRIVATE);

        try {
            // 1. Setup ClassLoader
            mClassLoader = new DexClassLoader(
                apkPath,
                dexOptDir.getAbsolutePath(),
                libDir.getAbsolutePath(),
                context.getClassLoader()
            );
            
            // 2. Setup Resources for the target APK
            setupResources(context, apkPath);
            
            Log.i(TAG, "Environment Initialized for " + packageName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize environment", e);
            return false;
        }
    }

    private void setupResources(Context context, String apkPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, apkPath);
            
            Resources hostRes = context.getResources();
            mResources = new Resources(assetManager, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
            Log.i(TAG, "Resources injected for APK: " + apkPath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup virtual resources", e);
        }
    }

    /**
     * Launches the target activity via system intent.
     * The Instrumentation hook WILL intercept this and redirect to StubActivity.
     */
    public void launch(Context context, String targetActivity) {
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

    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    public Resources getResources() {
        return mResources;
    }
}
