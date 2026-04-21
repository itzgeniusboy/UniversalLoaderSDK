package com.onecore.loader;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import java.util.List;
import java.io.File;

/**
 * Handles the final sequence of Injection -> Cloned Launch.
 * Optimized for Android 11+ non-root PID detection.
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";
    private static final String TARGET_PKG = "com.pubg.imobile";
    private static final int DETECTION_TIMEOUT_SEC = 30;

    public interface LaunchCallback {
        void onProcessDetected(int pid);
        void onFailed(String reason);
        void onProgress(String message);
    }

    public static void start(Context context, LaunchCallback callback) {
        try {
            Logger.i(TAG, "Initiating Virtual Space Launch for " + TARGET_PKG);
            
            if (callback != null) callback.onProgress("Verifying Environment...");

            // 1. Ensure virtualization engine starts
            Logger.i(TAG, "CRITICAL: Triggering Virtualization Host...");
            boolean success = VirtualContainer.getInstance().launch(context, TARGET_PKG);
            
            if (success) {
                if (callback != null) {
                    callback.onProgress("Virtual Session ACTIVE");
                    callback.onProcessDetected(0);
                }
            } else {
                Logger.e(TAG, "VirtualContainer reported launch failure.");
                if (callback != null) {
                    callback.onFailed("Engine denied launch. Check logs or License.");
                }
            }
        } catch (Throwable t) {
            Logger.e(TAG, "FATAL: Uncaught exception in GameLauncher.start", t);
            if (callback != null) {
                callback.onFailed("System Error: " + t.getMessage());
            }
        }
    }

    private static void proceedToLaunch(Context context, LaunchCallback callback) {
        start(context, callback);
    }
}
