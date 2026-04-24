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
        if (originalPath == null || sVirtualRoot == null) return originalPath;
        
        // Redirect standard data paths
        String dataPrefix = "/data/data/" + pkgName;
        String userPrefix = "/data/user/0/" + pkgName;

        if (originalPath.startsWith(dataPrefix) || originalPath.startsWith(userPrefix)) {
            String suffix = originalPath.substring(originalPath.indexOf(pkgName) + pkgName.length());
            String redirected = new File(sVirtualRoot, pkgName + "/data" + suffix).getAbsolutePath();
            
            File dir = new File(redirected).getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            
            return redirected;
        }
        
        return originalPath;
    }
}
