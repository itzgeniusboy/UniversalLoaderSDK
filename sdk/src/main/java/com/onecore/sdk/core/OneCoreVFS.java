package com.onecore.sdk.core;

import android.util.Log;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Advanced Virtual File System (VFS) to redirect game file requests 
 * and hide virtualization markers.
 */
public class OneCoreVFS {
    private static final String TAG = "OneCore-VFS";
    private static final Map<String, String> mRedirects = new HashMap<>();

    public static void init(String packageName, String virtualDataPath) {
        SafeExecutionManager.run("VFS Init", () -> {
            // Redirect common check paths
            mRedirects.put("/data/data/" + packageName, virtualDataPath);
            mRedirects.put("/data/user/0/" + packageName, virtualDataPath);
            
            // Redirect Data and OBB paths for Native Layer (Common patterns)
            mRedirects.put("/Android/obb/" + packageName, "/sdcard/Android/obb/" + packageName);
            mRedirects.put("/Android/data/" + packageName, "/sdcard/Android/data/" + packageName);
            mRedirects.put("/storage/emulated/0/Android/obb/" + packageName, "/sdcard/Android/obb/" + packageName);
            mRedirects.put("/storage/emulated/0/Android/data/" + packageName, "/sdcard/Android/data/" + packageName);
            mRedirects.put("/storage/self/primary/Android/obb/" + packageName, "/sdcard/Android/obb/" + packageName);
            mRedirects.put("/storage/self/primary/Android/data/" + packageName, "/sdcard/Android/data/" + packageName);
            mRedirects.put("/mnt/shell/emulated/0/Android/obb/" + packageName, "/sdcard/Android/obb/" + packageName);
            mRedirects.put("/data/media/0/Android/obb/" + packageName, "/sdcard/Android/obb/" + packageName);
            
            // Critical for UE4: Redirect the base OBB dir to search within sandbox
            mRedirects.put("/obb/" + packageName, "/sdcard/Android/obb/" + packageName);
            
            // BGMI specific: Internal Tencent paths that might leak
            mRedirects.put("/Tencent/TGPPA", virtualDataPath + "/files/TGPPA");
            mRedirects.put("/.Tencent", virtualDataPath + "/files/.Tencent");
            mRedirects.put("/Tencent", "/sdcard/Android/data/" + packageName + "/files/Tencent");

            Log.i(TAG, "OneCore-DEBUG: VFS initialized with BGMI/UE4 path masking.");
        });
    }

    public static File redirect(File file) {
        if (file == null) return null;
        String path = file.getPath();
        for (Map.Entry<String, String> entry : mRedirects.entrySet()) {
            if (path.contains(entry.getKey())) {
                return new File(path.replace(entry.getKey(), entry.getValue()));
            }
        }
        return file;
    }
}
