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
            if (callback != null) callback.onProgress("Detecting Game Installation...");

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
            if (callback != null) callback.onProgress("Launching " + targetPkg + "...");

            android.content.Intent intent = context.getPackageManager().getLaunchIntentForPackage(targetPkg);
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                if (callback != null) callback.onProcessDetected(1); // Simulated PID for success
            } else {
                if (callback != null) callback.onFailed("Could not create launch Intent");
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
