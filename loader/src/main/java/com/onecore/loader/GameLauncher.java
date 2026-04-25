package com.onecore.loader;

import android.content.Context;
import android.util.Log;

/**
 * Handles the launch of the game.
 * Simplified for minimal working version.
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";

    public interface LaunchCallback {
        void onProcessDetected(int pid);
        void onFailed(String reason);
        void onProgress(String message);
    }

    public static void start(Context context, LaunchCallback callback) {
        Log.i(TAG, "!! GameLauncher SESSION START !!");
        try {
            if (callback != null) {
                callback.onProgress("Starting Game Flow...");
                callback.onProcessDetected(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in GameLauncher", e);
            if (callback != null) callback.onFailed(e.getMessage());
        }
    }
}
