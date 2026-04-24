package com.onecore.sdk.core;

import java.io.File;
import com.onecore.sdk.utils.Logger;

/**
 * Handles virtual file system and path redirection.
 * Isolates virtual app data from host.
 */
public class VirtualStorage {
    private static final String TAG = "OneCore-Storage";

    public static String redirectPath(String originalPath, String pkgName, String virtualRoot) {
        if (originalPath == null) return null;
        
        // Redirect /data/data/pkg -> /virtual/root/data/pkg
        String dataPrefix = "/data/data/" + pkgName;
        String userPrefix = "/data/user/0/" + pkgName;
        
        if (originalPath.startsWith(dataPrefix) || originalPath.startsWith(userPrefix)) {
            String relative = originalPath.substring(originalPath.indexOf(pkgName) + pkgName.length());
            String redirected = virtualRoot + "/data" + relative;
            
            File dir = new File(redirected).getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            
            return redirected;
        }
        
        return originalPath;
    }
}
