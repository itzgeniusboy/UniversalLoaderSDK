package com.onecore.loader;

import android.content.Context;
import android.util.Log;

/**
 * Handles the launch of the game.
 * Simplified for minimal working version.
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";
    private static final String PKG_IMOBILE = "com.pubg.imobile";
    private static final String PKG_BGMI = "com.pubg.bgmi";

    public interface LaunchCallback {
        void onProcessDetected(int pid);
        void onFailed(String reason);
        void onProgress(String message);
    }

    public static void start(Context context, LaunchCallback callback) {
        Log.i(TAG, "!! GameLauncher SESSION START !!");
        try {
            if (callback != null) callback.onProgress("Initializing Virtual Environment...");

            String targetPkg = null;
            if (isPackageInstalled(context, PKG_IMOBILE)) {
                targetPkg = PKG_IMOBILE;
            } else if (isPackageInstalled(context, PKG_BGMI)) {
                targetPkg = PKG_BGMI;
            }

            if (targetPkg == null) {
                Log.e(TAG, "Game not found locally.");
                if (callback != null) callback.onFailed("Install APK first (BGMI/PUBG)");
                return;
            }

            Log.i(TAG, "Found target package: " + targetPkg);
            
            // REAL PROGRESS: Using VirtualContainer
            com.onecore.sdk.VirtualContainer container = com.onecore.sdk.VirtualContainer.getInstance(context);
            
            // In a real scenario, we'd get the source APK path. 
            // For now, we simulate the "install" from the public APK location.
            String sourcePath = context.getPackageManager().getApplicationInfo(targetPkg, 0).sourceDir;
            
            if (callback != null) callback.onProgress("Preparing Virtual Space...");
            boolean installed = container.installApk(sourcePath, targetPkg);
            
            if (!installed) {
                if (callback != null) callback.onFailed("Failed to install into virtual container");
                return;
            }

            if (callback != null) callback.onProgress("Launching in Container...");
            boolean launched = container.launchApp(context, targetPkg);

            if (launched) {
                if (callback != null) callback.onProcessDetected(1); 
            } else {
                if (callback != null) callback.onFailed("Container launch failed");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in GameLauncher", e);
            if (callback != null) callback.onFailed(e.getMessage());
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
