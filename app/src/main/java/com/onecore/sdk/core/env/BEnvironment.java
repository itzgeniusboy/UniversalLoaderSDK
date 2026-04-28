package com.onecore.sdk.core.env;

import android.content.Context;
import java.io.File;

/**
 * Manages the virtual environment directory structure.
 * Mimics 'top.niunaijun.blackbox.core.env.BEnvironment'
 */
public class BEnvironment {
    private static File sVirtualRoot;

    public static void init(Context context) {
        sVirtualRoot = new File(context.getFilesDir().getAbsolutePath() + "/virtual");
        if (!sVirtualRoot.exists()) {
            sVirtualRoot.mkdirs();
        }
    }

    public static File getVirtualRoot() {
        return sVirtualRoot;
    }

    public static File getDataDir(String packageName) {
        return new File(sVirtualRoot, "data/" + packageName);
    }

    public static File getLibDir(String packageName) {
        return new File(sVirtualRoot, "data/" + packageName + "/lib");
    }
    
    public static File getObbDir(String packageName) {
        return new File(sVirtualRoot, "obb/" + packageName);
    }
}
