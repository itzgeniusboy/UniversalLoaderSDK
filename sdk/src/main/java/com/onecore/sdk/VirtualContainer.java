package com.onecore.sdk;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import dalvik.system.DexClassLoader;

/**
 * Basic Virtual Container System for OneCore.
 * Handles APK "installation" (copying to internal storage) and basic launching.
 */
public class VirtualContainer {
    private static final String TAG = "VirtualContainer";
    private static VirtualContainer sInstance;
    private Context mContext;

    private VirtualContainer(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public static synchronized VirtualContainer getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new VirtualContainer(context);
        }
        return sInstance;
    }

    /**
     * Installs an APK into the virtual container by copying it to internal storage.
     */
    public boolean installApk(String sourcePath, String packageName) {
        Log.i(TAG, "Installing APK: " + packageName + " from " + sourcePath);
        File installDir = new File(mContext.getFilesDir(), "container/" + packageName);
        if (!installDir.exists() && !installDir.mkdirs()) {
            Log.e(TAG, "Failed to create install directory");
            return false;
        }

        File targetApk = new File(installDir, "base.apk");
        try {
            copyFile(new File(sourcePath), targetApk);
            Log.i(TAG, "APK copied to " + targetApk.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy APK", e);
            return false;
        }
    }

    /**
     * "Launches" the app by setting up a DexClassLoader.
     * In a real implementation, this would start a StubActivity.
     */
    public boolean launchApp(Context context, String packageName) {
        Log.i(TAG, "Launching App: " + packageName);
        File installDir = new File(mContext.getFilesDir(), "container/" + packageName);
        File targetApk = new File(installDir, "base.apk");

        if (!targetApk.exists()) {
            Log.e(TAG, "APK not found for " + packageName);
            return false;
        }

        File dexOptDir = new File(installDir, "oat");
        if (!dexOptDir.exists() && !dexOptDir.mkdirs()) {
            Log.e(TAG, "Failed to create dexopt directory");
            return false;
        }

        try {
            // Basic classloader setup - this validates the APK can be loaded
            DexClassLoader classLoader = new DexClassLoader(
                targetApk.getAbsolutePath(),
                dexOptDir.getAbsolutePath(),
                null,
                context.getClassLoader()
            );
            Log.i(TAG, "ClassLoader established for " + packageName);
            
            // For now, we just log success. 
            // Future steps will implement StubActivity for true UI launch.
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app", e);
            return false;
        }
    }

    private void copyFile(File source, File dest) throws IOException {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }
}
