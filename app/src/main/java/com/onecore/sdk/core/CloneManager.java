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
        Logger.i(TAG, "prepareClone: Starting for package: " + packageName);
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            try {
                info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS);
                Logger.d(TAG, "Source package found: " + packageName);
            } catch (PackageManager.NameNotFoundException e) {
                Logger.e(TAG, "Source package NOT FOUND: " + packageName);
                return false;
            }
            
            // 1. Generate Virtual Identity
            String virtualPkg = "com.onecore.cloned." + (packageName.contains(".") ? packageName.substring(packageName.lastIndexOf('.') + 1) : packageName);
            Logger.d(TAG, "Generated Virtual Package Name: " + virtualPkg);
            
            // CRITICAL: Preserve original sourceDir for ClassLoader
            info.packageName = virtualPkg;
            info.applicationInfo.packageName = virtualPkg;
            
            // 2. Define Isolated Sandbox Paths
            String virtualRoot = context.getFilesDir().getAbsolutePath() + "/virtual/" + packageName;
            Logger.i(TAG, "Isolated Workspace Path: " + virtualRoot);
            info.applicationInfo.dataDir = virtualRoot;
            info.applicationInfo.nativeLibraryDir = virtualRoot + "/lib";
            
            cache.put(packageName, info);
            
            // 3. Initialize Physical Sandbox Directories
            File dataDir = new File(virtualRoot);
            if (!dataDir.exists()) {
                Logger.d(TAG, "Creating new virtual directories...");
                dataDir.mkdirs();
                new File(virtualRoot, "data/files").mkdirs();
                new File(virtualRoot, "data/cache").mkdirs();
                new File(virtualRoot, "lib").mkdirs();
                new File(virtualRoot, "obb").mkdirs();
            } else {
                Logger.d(TAG, "Virtual directories already exist.");
            }

            // Map OBBs
            Logger.i(TAG, "Triggering OBB Mapping sequence...");
            mapObb(context, packageName, virtualRoot);
            
            // 4. Setup Virtual Environment
            Logger.i(TAG, "Triggering Native Library Mapping sequence...");
            setupVirtualEnv(context, packageName, info.applicationInfo);
            
            Logger.i(TAG, "!! CLONE PREPARATION COMPLETE !! Result: SUCCESS");
            return true;
        } catch (Throwable e) {
            Logger.e(TAG, "FATAL ERROR during clone preparation: " + e.getMessage(), e);
            return false;
        }
    }

    private void mapObb(Context context, String packageName, String virtualRoot) {
        try {
            // Priority: Real OBB path
            File systemObbDir = new File("/storage/emulated/0/Android/obb/" + packageName);
            if (!systemObbDir.exists()) {
                systemObbDir = new File("/sdcard/Android/obb/" + packageName);
            }
            
            File virtualObbDir = new File(virtualRoot, "obb");
            if (!virtualObbDir.exists()) virtualObbDir.mkdirs();

            if (systemObbDir.exists() && systemObbDir.isDirectory()) {
                File[] obbs = systemObbDir.listFiles();
                if (obbs != null && obbs.length > 0) {
                    for (File obb : obbs) {
                        File target = new File(virtualObbDir, obb.getName());
                        // If link fails, UE4 will just use the redirected path which we already hooked anyway
                        try {
                            if (!target.exists()) {
                                Os.symlink(obb.getAbsolutePath(), target.getAbsolutePath());
                                Logger.d(TAG, "OBB SYMLINK SUCCESS: " + obb.getName());
                            }
                        } catch (Exception e) {
                            Logger.w(TAG, "Symlink failed, natively handled via redirect_path: " + e.getMessage());
                            // Fallback: If symlink fails, the native open() hook will still catch it
                        }
                    }
                } else {
                    Logger.w(TAG, "OBB Folder is EMPTY at " + systemObbDir.getAbsolutePath());
                }
            } else {
                Logger.e(TAG, "CRITICAL: OBB Folder NOT FOUND. Game will Black Screen.");
            }
        } catch (Exception e) {
            Logger.e(TAG, "OBB Mapping Exception", e);
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
