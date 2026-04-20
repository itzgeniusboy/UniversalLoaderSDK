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
 * Orchestrates the "Deep Clone" state.
 * Manages metadata and isolated filesystem mapping for the Sandbox.
 */
public class CloneManager {
    private static final String TAG = "CloneManager";
    private static CloneManager instance;
    private final Map<String, PackageInfo> cache = new HashMap<>();

    private CloneManager() {}

    public static synchronized CloneManager getInstance() {
        if (instance == null) {
            instance = new CloneManager();
        }
        return instance;
    }

    public boolean prepareClone(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS);
            
            // Apply Fake Identity Metadata
            info.packageName = "com.onecore.cloned." + packageName.substring(packageName.lastIndexOf('.') + 1);
            info.applicationInfo.packageName = info.packageName;
            info.applicationInfo.dataDir = context.getFilesDir().getAbsolutePath() + "/sandbox/" + packageName;
            
            cache.put(packageName, info);
            
            // Initialize Physical Sandbox Directories
            File dataDir = new File(info.applicationInfo.dataDir);
            if (!dataDir.exists()) dataDir.mkdirs();
            
            Logger.i(TAG, "Clone Ready: Original=" + packageName + " -> Fake=" + info.packageName);
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to prepare CloneManager metadata", e);
            return false;
        }
    }

    public PackageInfo getClonedPackage(String originalPackageName) {
        return cache.get(originalPackageName);
    }
}
