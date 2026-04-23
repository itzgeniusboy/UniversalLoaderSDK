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
            String virtualRoot = context.getFilesDir().getAbsolutePath() + "/virtual/" + packageName;
            info.applicationInfo.dataDir = virtualRoot;
            info.applicationInfo.nativeLibraryDir = virtualRoot + "/lib";
            
            cache.put(packageName, info);
            
            // 3. Initialize Physical Sandbox Directories
            File dataDir = new File(virtualRoot);
            if (!dataDir.exists()) {
                dataDir.mkdirs();
                new File(virtualRoot, "data/files").mkdirs();
                new File(virtualRoot, "data/cache").mkdirs();
                new File(virtualRoot, "lib").mkdirs();
                new File(virtualRoot, "obb").mkdirs();
            }
            
            // 4. Setup Virtual Environment
            setupVirtualEnv(context, packageName, info.applicationInfo);
            
            Logger.i(TAG, "Deep Clone Prepared successfully: " + packageName);
            return true;
        } catch (Throwable e) {
            Logger.e(TAG, "Clone Preparation Error: " + e.getMessage(), e);
            return false;
        }
    }

    private void setupVirtualEnv(Context context, String originalPkg, ApplicationInfo virtualApp) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo originalApp = pm.getApplicationInfo(originalPkg, 0);
            
            File sourceLibDir = new File(originalApp.nativeLibraryDir);
            File virtualLibDir = new File(virtualApp.nativeLibraryDir);
            
            if (!virtualLibDir.exists()) virtualLibDir.mkdirs();
            
            if (sourceLibDir.exists() && sourceLibDir.isDirectory()) {
                File[] libs = sourceLibDir.listFiles();
                if (libs != null) {
                    for (File lib : libs) {
                        try {
                            File target = new File(virtualLibDir, lib.getName());
                            if (target.exists()) target.delete();
                            Os.symlink(lib.getAbsolutePath(), target.getAbsolutePath());
                            Logger.d(TAG, "Mapped Library: " + lib.getName());
                        } catch (Exception e) {
                            Logger.w(TAG, "Failed to symlink " + lib.getName() + ": " + e.getMessage());
                            // Fallback: Just point to original path if symlink fails? 
                            // No, DexClassLoader needs a directory.
                        }
                    }
                }
            } else {
                Logger.w(TAG, "Source native library directory not found: " + originalApp.nativeLibraryDir);
            }
        } catch (Throwable e) {
            Logger.e(TAG, "Virtual Lib Mapping Failure", e);
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
