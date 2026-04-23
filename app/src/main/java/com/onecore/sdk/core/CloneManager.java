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

            // Map OBBs
            mapObb(context, packageName, virtualRoot);
            
            // 4. Setup Virtual Environment
            setupVirtualEnv(context, packageName, info.applicationInfo);
            
            Logger.i(TAG, "Deep Clone Prepared successfully: " + packageName);
            return true;
        } catch (Throwable e) {
            Logger.e(TAG, "Clone Preparation Error: " + e.getMessage(), e);
            return false;
        }
    }

    private void mapObb(Context context, String packageName, String virtualRoot) {
        try {
            File systemObbDir = new File("/storage/emulated/0/Android/obb/" + packageName);
            File virtualObbDir = new File(virtualRoot, "obb");
            if (!virtualObbDir.exists()) virtualObbDir.mkdirs();

            if (systemObbDir.exists() && systemObbDir.isDirectory()) {
                File[] obbs = systemObbDir.listFiles();
                if (obbs != null) {
                    for (File obb : obbs) {
                        File target = new File(virtualObbDir, obb.getName());
                        if (!target.exists()) {
                            Os.symlink(obb.getAbsolutePath(), target.getAbsolutePath());
                            Logger.d(TAG, "OBB Mapped: " + obb.getName());
                        }
                    }
                }
            } else {
                Logger.w(TAG, "System OBB not found at: " + systemObbDir.getAbsolutePath());
            }
        } catch (Exception e) {
            Logger.e(TAG, "OBB Mapping Failed", e);
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
                            Logger.d(TAG, "Mapped Library: " + lib.getName() + " -> " + lib.getAbsolutePath());
                        } catch (Exception e) {
                            Logger.e(TAG, "Failed to symlink lib " + lib.getName(), e);
                        }
                    }
                }
            } else {
                Logger.e(TAG, "Source native library directory NOT FOUND: " + originalApp.nativeLibraryDir);
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
