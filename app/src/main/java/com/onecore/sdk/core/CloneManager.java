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
            ApplicationInfo appInfo;
            try {
                appInfo = pm.getPackageInfo(packageName, 0).applicationInfo;
            } catch (PackageManager.NameNotFoundException e) {
                Logger.e(TAG, "Source package NOT FOUND: " + packageName);
                return false;
            }

            // Task 1: Proper APK Parsing using getPackageArchiveInfo
            PackageInfo info = pm.getPackageArchiveInfo(appInfo.sourceDir, 
                PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS);
            
            if (info == null) {
                Logger.e(TAG, "Failed to parse APK archive: " + appInfo.sourceDir);
                return false;
            }
            
            // Sync ApplicationInfo since getPackageArchiveInfo might have empty applicationInfo in some Android versions
            if (info.applicationInfo == null) {
                info.applicationInfo = appInfo;
            } else {
                // Ensure paths are synced from the real installation if we are using an installed app
                info.applicationInfo.sourceDir = appInfo.sourceDir;
                info.applicationInfo.publicSourceDir = appInfo.publicSourceDir;
            }

            Logger.d(TAG, "Source package metadata parsed: " + packageName);
            
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
            
            // Task 5: Native Library Fix - Extract all .so files
            String libDirStr = virtualRoot + "/lib";
            info.applicationInfo.nativeLibraryDir = libDirStr;
            try {
                Field f = ApplicationInfo.class.getDeclaredField("primaryCpuAbi");
                f.setAccessible(true);
                f.set(info.applicationInfo, f.get(appInfo));
            } catch (Throwable ignored) {}
            info.applicationInfo.targetSdkVersion = appInfo.targetSdkVersion;
            
            cache.put(packageName, info);
            
            // 3. Initialize Physical Sandbox Directories
            File dataDir = new File(virtualRoot);
            if (!dataDir.exists()) {
                Logger.d(TAG, "Creating new virtual directories...");
                dataDir.mkdirs();
                new File(virtualRoot, "data/files").mkdirs();
                new File(virtualRoot, "data/cache").mkdirs();
                new File(libDirStr).mkdirs();
                new File(virtualRoot, "obb").mkdirs();
            } else {
                Logger.d(TAG, "Virtual directories already exist.");
            }

            // Map OBBs
            Logger.i(TAG, "Triggering OBB Mapping sequence...");
            mapObb(context, packageName, virtualRoot);
            
            // Task 5: Extraction
            Logger.i(TAG, "Triggering Native Library Extraction...");
            File libDir = new File(libDirStr);
            if (libDir.list() == null || libDir.list().length == 0) {
                extractNativeLibs(appInfo.nativeLibraryDir, libDirStr);
            } else {
                Logger.d(TAG, "Native libraries already extracted.");
            }
            
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

    private void extractNativeLibs(String sourceDir, String targetDir) {
        File src = new File(sourceDir);
        File dst = new File(targetDir);
        if (!dst.exists()) dst.mkdirs();

        if (src.exists() && src.isDirectory()) {
            recursiveCopyLibs(src, dst);
        } else {
            Logger.e(TAG, "Source lib dir missing: " + sourceDir);
        }
    }

    private void recursiveCopyLibs(File src, File dst) {
        File[] files = src.listFiles();
        if (files == null) return;
        
        String myAbi = android.os.Build.SUPPORTED_ABIS[0];
        
        for (File f : files) {
            if (f.isDirectory()) {
                // If it's an ABI directory, only enter if it matches current device ABI
                if (isAbiDir(f.getName())) {
                    if (f.getName().equals(myAbi) || f.getName().startsWith(myAbi.split("-")[0])) {
                        recursiveCopyLibs(f, dst); // Flatten into target lib dir
                    }
                } else {
                    File nextDst = new File(dst, f.getName());
                    nextDst.mkdirs();
                    recursiveCopyLibs(f, nextDst);
                }
            } else if (f.getName().endsWith(".so")) {
                try {
                    File targetFile = new File(dst, f.getName());
                    if (targetFile.exists()) targetFile.delete();
                    java.nio.file.Files.copy(f.toPath(), targetFile.toPath());
                    Logger.v(TAG, "Extracted lib: " + f.getName());
                } catch (Exception e) {
                    Logger.e(TAG, "Extraction failed for " + f.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private boolean isAbiDir(String name) {
        return name.equals("arm64-v8a") || name.equals("armeabi-v7a") || name.equals("x86") || name.equals("x86_64");
    }

    private static class Os {
        // Helper to avoid directly using android.system.Os for older API compatibility
        public static void symlink(String oldPath, String newPath) throws Exception {
            Class<?> osClass = Class.forName("android.system.Os");
            osClass.getMethod("symlink", String.class, String.class).invoke(null, oldPath, newPath);
        }
    }

    private ClassLoader guestClassLoader;
    private android.content.res.Resources guestResources;
    private Context hostContext;

    public Context getHostContext() {
        return hostContext;
    }

    public void setHostContext(Context hostContext) {
        this.hostContext = hostContext;
    }

    public ClassLoader getClassLoader() {
        return guestClassLoader;
    }

    public void setClassLoader(ClassLoader loader) {
        this.guestClassLoader = loader;
    }

    public android.content.res.Resources getResources() {
        return guestResources;
    }

    public void setResources(android.content.res.Resources res) {
        this.guestResources = res;
    }

    public PackageInfo getClonedPackage(String originalPackageName) {
        return cache.get(originalPackageName);
    }
}
