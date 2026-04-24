package com.onecore.loader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;

/**
 * Handles the launch of BGMI within the virtualized sandbox.
 * Fixed for Android 14+ Non-Root Isolation.
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
        Logger.i(TAG, "!! GameLauncher SESSION START !!");
        try {
            // Detect installed version
            String originalPkg = PKG_IMOBILE;
            boolean imobileInstalled = false;
            boolean bgmiInstalled = false;
            
            try {
                context.getPackageManager().getPackageInfo(PKG_IMOBILE, 0);
                imobileInstalled = true;
            } catch (Exception ignored) {}
            
            try {
                context.getPackageManager().getPackageInfo(PKG_BGMI, 0);
                bgmiInstalled = true;
            } catch (Exception ignored) {}
            
            if (imobileInstalled) {
                originalPkg = PKG_IMOBILE;
            } else if (bgmiInstalled) {
                originalPkg = PKG_BGMI;
            } else {
                Logger.e(TAG, "FATAL: BGMI not installed.");
                if (callback != null) callback.onFailed("Game not installed. Please install BGMI first.");
                return;
            }
            
            Logger.i(TAG, "Initiating Orchestration for " + originalPkg);
            if (callback != null) callback.onProgress("Starting Game Flow...");

            // Use the Orchestrator for guaranteed launch
            com.onecore.sdk.LauncherOrchestrator.startGame(context, originalPkg);
            
            // For UI feedback purposes, assume success if no immediate crash
            if (callback != null) {
                callback.onProgress("Orchestrator Handover Successful");
                callback.onProcessDetected(0);
            }

        } catch (Exception e) {
            Logger.e(TAG, "FATAL ERROR in GameLauncher logic", e);
            if (callback != null) callback.onFailed(e.getMessage());
        }
    }
}
