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

        // Redirect /data/data/pkg_name/ to /data/data/com.onecore/virtual/pkg_name/
        String appDataPath = "/data/data/" + packageName;
        String userAppDataPath = "/data/user/0/" + packageName;

        if (originalPath.startsWith(appDataPath)) {
            return originalPath.replace(appDataPath, VIRTUAL_ROOT + packageName);
        } else if (originalPath.startsWith(userAppDataPath)) {
            return originalPath.replace(userAppDataPath, VIRTUAL_ROOT + packageName);
        }

        return originalPath;
    }

    public static void ensureVirtualEnv(Context context, String packageName) {
        File virtualDir = new File(context.getFilesDir(), "virtual/" + packageName);
        if (!virtualDir.exists()) {
            virtualDir.mkdirs();
            new File(virtualDir, "files").mkdirs();
            new File(virtualDir, "cache").mkdirs();
            new File(virtualDir, "shared_prefs").mkdirs();
            new File(virtualDir, "databases").mkdirs();
        }
    }

    public static String getVirtualRoot(Context context, String packageName) {
        return new File(context.getFilesDir(), "virtual/" + packageName).getAbsolutePath();
    }
}
