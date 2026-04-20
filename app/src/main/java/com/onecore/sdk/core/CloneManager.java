package com.onecore.sdk.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.onecore.sdk.utils.Logger;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the "Cloning" state of target applications.
 * Prepares isolated directories and environment variables for the sandbox.
 */
public class CloneManager {
    private static final String TAG = "CloneManager";
    private static CloneManager instance;
    private final Map<String, PackageInfo> clonedPackages = new HashMap<>();

    private CloneManager() {}

    public static synchronized CloneManager getInstance() {
        if (instance == null) {
            instance = new CloneManager();
        }
        return instance;
    }

    /**
     * Prepares the app for cloning.
     * This involves caching metadata and ensuring the virtual filesystem is ready.
     */
    public boolean prepareClone(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES);
            clonedPackages.put(packageName, info);
            
            // Create Sandbox Directories
            File sandboxDir = new File(context.getFilesDir(), "sandbox/" + packageName);
            if (!sandboxDir.exists()) sandboxDir.mkdirs();
            
            // Subdirectories for internal isolation
            new File(sandboxDir, "data").mkdirs();
            new File(sandboxDir, "cache").mkdirs();
            new File(sandboxDir, "libs").mkdirs();

            Logger.i(TAG, "Application " + packageName + " is prepared for Real Cloning.");
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to prepare clone for " + packageName, e);
            return false;
        }
    }

    public PackageInfo getClonedPackage(String packageName) {
        return clonedPackages.get(packageName);
    }
    
    public String getSandboxDataPath(Context context, String packageName) {
        return new File(context.getFilesDir(), "sandbox/" + packageName + "/data").getAbsolutePath();
    }
}
