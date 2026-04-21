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
            
            // 1. Generate Virtual Identity
            String virtualPkg = "com.onecore.cloned." + packageName.substring(packageName.lastIndexOf('.') + 1);
            
            // CRITICAL: Preserve original sourceDir for ClassLoader
            info.packageName = virtualPkg;
            info.applicationInfo.packageName = virtualPkg;
            
            // 2. Define Isolated Sandbox Paths
            String sandboxRoot = context.getFilesDir().getAbsolutePath() + "/sandbox/" + packageName;
            info.applicationInfo.dataDir = sandboxRoot;
            info.applicationInfo.nativeLibraryDir = sandboxRoot + "/lib";
            
            cache.put(packageName, info);
            
            // 3. Initialize Physical Sandbox Directories
            File dataDir = new File(sandboxRoot);
            if (!dataDir.exists()) {
                dataDir.mkdirs();
                new File(sandboxRoot, "files").mkdirs();
                new File(sandboxRoot, "cache").mkdirs();
                new File(sandboxRoot, "lib").mkdirs();
            }
            
            // 4. Setup Virtual Environment (Standard for OneCore Engine)
            setupVirtualEnv(context, packageName, info.applicationInfo);
            
            Logger.i(TAG, "Deep Clone Prepared: " + packageName + " -> " + virtualPkg);
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Clone Preparation Error", e);
            return false;
        }
    }

    private void setupVirtualEnv(Context context, String originalPkg, ApplicationInfo virtualApp) {
        // Here we map the real APK resources to the virtual context
        // In a real implementation, we would symlink native libraries from the source APK
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo originalApp = pm.getApplicationInfo(originalPkg, 0);
            
            File sourceLibDir = new File(originalApp.nativeLibraryDir);
            File virtualLibDir = new File(virtualApp.nativeLibraryDir);
            
            if (sourceLibDir.exists() && sourceLibDir.isDirectory()) {
                File[] libs = sourceLibDir.listFiles();
                if (libs != null) {
                    for (File lib : libs) {
                        // Create symlinks to avoid copying massive game binaries
                        Os.symlink(lib.getAbsolutePath(), virtualLibDir.getAbsolutePath() + "/" + lib.getName());
                    }
                }
            }
        } catch (Exception e) {
            Logger.w(TAG, "Virtual Lib Mapping Warning: " + e.getMessage());
        }
    }

    private static class Os {
        // Helper to avoid directly using android.system.Os for older API compatibility
        public static void symlink(String oldPath, String newPath) throws Exception {
            Class<?> osClass = Class.forName("android.system.Os");
            osClass.getMethod("symlink", String.class, String.class).invoke(null, oldPath, newPath);
        }
    }

    public PackageInfo getClonedPackage(String originalPackageName) {
        return cache.get(originalPackageName);
    }
}
