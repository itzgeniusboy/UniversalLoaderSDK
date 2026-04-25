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
     * Launches the target activity inside a StubActivity.
     */
    public void launch(Context context, String targetActivity) {
        if (mClassLoader == null) {
            Log.e(TAG, "Cannot launch: ClassLoader not initialized. Call installApk first.");
            return;
        }

        // Install Instrumentation Hook before launching any activity
        com.onecore.sdk.core.HookManager.installInstrumentationHook();

        Log.i(TAG, "Launching target activity: " + targetActivity);
        
        try {
            Intent intent = new Intent();
            // Use the StubActivity for UI hosting
            intent.setClassName(context.getPackageName(), "com.onecore.loader.StubActivity");
            intent.putExtra("target_activity", targetActivity);
            intent.putExtra("target_package", mPackageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start StubActivity", e);
        }
    }

    /**
     * Loads and initializes the target application instance.
     */
    public void bindApplication(Context context, String applicationClassName) {
        if (mClassLoader == null) return;
        
        try {
            Log.i(TAG, "Binding Application: " + applicationClassName);
            
            Class<?> appClass = mClassLoader.loadClass(applicationClassName);
            mTargetApplication = (android.app.Application) appClass.newInstance();
            
            // We would need to attach context here. In a full engine, we use instrumentation.
            // For now, we simulate the call if possible.
            Method attach = android.app.Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            attach.invoke(mTargetApplication, context);
            
            mTargetApplication.onCreate();
            Log.i(TAG, "Target Application bound and onCreate called.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind application", e);
        }
    }

    public android.app.Application getTargetApplication() {
        return mTargetApplication;
    }
}
