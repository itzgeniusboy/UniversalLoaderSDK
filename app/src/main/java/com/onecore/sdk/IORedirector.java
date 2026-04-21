package com.onecore.sdk;

import java.io.File;
import android.content.Context;

/**
 * Handles file path redirection for the virtual environment.
 * Maps standard app paths to the OneCore virtual storage.
 */
public class IORedirector {
    private static final String VIRTUAL_ROOT = "/data/data/com.onecore/virtual/";

    public static String redirectPath(String originalPath, String packageName) {
        if (originalPath == null) return null;

        // 1. Data Redirection
        String appDataPath = "/data/data/" + packageName;
        String userAppDataPath = "/data/user/0/" + packageName;
        String virtualData = getVirtualRoot(null, packageName) + "/data";

        // 2. OBB Redirection
        String obbPath = "/storage/emulated/0/Android/obb/" + packageName;
        String virtualObb = getVirtualRoot(null, packageName) + "/obb";

        if (originalPath.startsWith(appDataPath)) {
            return originalPath.replace(appDataPath, virtualData);
        } else if (originalPath.startsWith(userAppDataPath)) {
            return originalPath.replace(userAppDataPath, virtualData);
        } else if (originalPath.startsWith(obbPath)) {
            return originalPath.replace(obbPath, virtualObb);
        }

        return originalPath;
    }

    public static void ensureVirtualEnv(Context context, String packageName) {
        File virtualDir = new File(context.getFilesDir(), "virtual/" + packageName);
        if (!virtualDir.exists()) {
            virtualDir.mkdirs();
        }
        
        // Setup internal hierarchies
        new File(virtualDir, "data/files").mkdirs();
        new File(virtualDir, "data/cache").mkdirs();
        new File(virtualDir, "data/shared_prefs").mkdirs();
        new File(virtualDir, "obb").mkdirs();
        
        Logger.i("IORedirector", "Virtual Space Initialized for: " + packageName);
    }

    public static String getVirtualRoot(Context context, String packageName) {
        // Fallback for cases where context is null (using relative path to files dir)
        if (context == null) {
            return "/data/data/com.onecore.loader/files/virtual/" + packageName;
        }
        return new File(context.getFilesDir(), "virtual/" + packageName).getAbsolutePath();
    }
}
