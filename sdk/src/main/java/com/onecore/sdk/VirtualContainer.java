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
     * The Instrumentation hook will intercept this and redirect to StubActivity.
     */
    public void launch(Context context, String targetActivity) {
        if (mClassLoader == null) {
            Log.e(TAG, "Cannot launch: ClassLoader not initialized. Call installApk first.");
            return;
        }

        Log.i(TAG, "Requesting launch of target activity: " + targetActivity);
        
        try {
            Intent intent = new Intent();
            intent.setClassName(mPackageName, targetActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "Launch intent sent to system.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch target via system intent", e);
            // Fallback: Manually launch via StubActivity if hook didn't catch it
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), "com.onecore.loader.StubActivity");
            intent.putExtra("target_activity", targetActivity);
            intent.putExtra("target_package", mPackageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
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
