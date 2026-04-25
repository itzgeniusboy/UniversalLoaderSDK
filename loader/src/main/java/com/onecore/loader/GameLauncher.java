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
            
            // REAL PROGRESS: Using OneCore SDK and VirtualContainer
            com.onecore.sdk.OneCoreSDK.init(context);
            com.onecore.sdk.VirtualContainer container = com.onecore.sdk.VirtualContainer.getInstance();
            
            String sourcePath = context.getPackageManager().getApplicationInfo(targetPkg, 0).sourceDir;
            
            if (callback != null) callback.onProgress("Preparing Virtual Space...");
            boolean installed = container.installApk(context, sourcePath, targetPkg);
            
            if (!installed) {
                if (callback != null) callback.onFailed("Failed to initialize virtual space");
                return;
            }

            if (callback != null) callback.onProgress("Launching in Container...");
            
            // Phase 2: Bind Application
            android.content.pm.ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(targetPkg, 0);
            if (appInfo.className != null) {
                container.bindApplication(context, appInfo.className);
            } else {
                Log.d(TAG, "No custom Application class found for " + targetPkg);
            }
            
            // Launch the main activity
            String targetActivity = context.getPackageManager().getLaunchIntentForPackage(targetPkg).getComponent().getClassName();
            container.launch(context, targetActivity);

            if (callback != null) callback.onProcessDetected(1); 

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
