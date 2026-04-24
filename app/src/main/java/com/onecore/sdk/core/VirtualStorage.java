package com.onecore.sdk.core;

import java.io.File;
import com.onecore.sdk.utils.Logger;

/**
 * Handles virtual file system and path redirection.
 * Isolates virtual app data from host.
 */
public class VirtualStorage {
    private static final String TAG = "OneCore-Storage";
    private static String sVirtualRoot;

    public static void init(String root) {
        sVirtualRoot = root;
        new File(root).mkdirs();
    }

    public static File getVirtualDir(String pkgName, String type) {
        File dir = new File(sVirtualRoot, pkgName + "/" + type);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static String redirectPath(String originalPath, String pkgName) {
        if (originalPath == null || sVirtualRoot == null || pkgName == null) return originalPath;
        
        // Redirect standard data paths
        String dataPrefix = "/data/data/" + pkgName;
        String userPrefix = "/data/user/0/" + pkgName;
        String mntPrefix = "/mnt/sdcard/Android/data/" + pkgName;
        String obbPrefix = "/storage/emulated/0/Android/obb/" + pkgName;

        if (originalPath.startsWith(dataPrefix) || originalPath.startsWith(userPrefix) || originalPath.startsWith(mntPrefix) || originalPath.startsWith(obbPrefix)) {
            String suffix = "";
            if (originalPath.startsWith(dataPrefix)) suffix = originalPath.substring(dataPrefix.length());
            else if (originalPath.startsWith(userPrefix)) suffix = originalPath.substring(userPrefix.length());
            else if (originalPath.startsWith(obbPrefix)) suffix = originalPath.substring(obbPrefix.length());
            else suffix = originalPath.substring(mntPrefix.length());

            String redirected = new File(sVirtualRoot, pkgName + "/data" + suffix).getAbsolutePath();
            
            // Ensure parent directory exists for the redirected path
            File file = new File(redirected);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            
            Logger.v(TAG, "Redirected: " + originalPath + " -> " + redirected);
            return redirected;
        }
        
        return originalPath;
    }
}
