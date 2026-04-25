package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import dalvik.system.DexClassLoader;

/**
 * Minimal Working Virtual Container Engine (Phase 1).
 */
public class VirtualContainer {
    private static final String TAG = "VirtualContainer";
    private static VirtualContainer sInstance;
    
    private String mApkPath;
    private DexClassLoader mClassLoader;
    private String mPackageName;

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
            mClassLoader = new DexClassLoader(
                apkPath,
                dexOptDir.getAbsolutePath(),
                libDir.getAbsolutePath(),
                context.getClassLoader()
            );
            Log.i(TAG, "DexClassLoader initialized successfully.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize DexClassLoader", e);
            return false;
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

        Log.i(TAG, "Launching target activity: " + targetActivity);
        
        // We use the StubActivity defined in the loader module for UI hosting
        try {
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), "com.onecore.loader.StubActivity");
            intent.putExtra("target_activity", targetActivity);
            intent.putExtra("target_package", mPackageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start StubActivity", e);
        }
    }

    public DexClassLoader getClassLoader() {
        return mClassLoader;
    }
    
    public String getApkPath() {
        return mApkPath;
    }
}
