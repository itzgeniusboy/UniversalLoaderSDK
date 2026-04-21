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
    private static String TARGET_PKG = "com.pubg.imobile";
    private static final String FALLBACK_PKG = "com.pubg.bgmi";

    public interface LaunchCallback {
        void onProcessDetected(int pid);
        void onFailed(String reason);
        void onProgress(String message);
    }

    public static void start(Context context, LaunchCallback callback) {
        try {
            // Check which version is installed
            String pkg = TARGET_PKG;
            try {
                context.getPackageManager().getPackageInfo(TARGET_PKG, 0);
            } catch (Exception e) {
                pkg = FALLBACK_PKG;
            }

            final String finalPkg = pkg;
            Logger.i(TAG, "Initiating Virtual Space Launch for " + finalPkg);
            
            if (callback != null) callback.onProgress("Verifying Environment...");

            // Timeout Handler (10 Seconds)
            final Handler handler = new Handler(Looper.getMainLooper());
            final Runnable timeoutTask = () -> {
                Logger.e(TAG, "Launch confirmation TIMEOUT for " + finalPkg);
                if (callback != null) callback.onFailed("Launch Timeout (System Busy)");
            };
            handler.postDelayed(timeoutTask, 10000);

            // 1. Ensure virtualization engine starts with confirmation callback
            Logger.i(TAG, "CRITICAL: Triggering Virtualization Host...");
            boolean initiated = VirtualContainer.getInstance().launch(context, finalPkg, new VirtualContainer.LaunchCallback() {
                @Override
                public void onLaunchSuccess() {
                    handler.removeCallbacks(timeoutTask);
                    Logger.i(TAG, "Launch confirmed by Kernel.");
                    if (callback != null) {
                        callback.onProgress("Virtual Session ACTIVE");
                        callback.onProcessDetected(0);
                    }
                }

                @Override
                public void onLaunchFailed(String reason) {
                    handler.removeCallbacks(timeoutTask);
                    Logger.e(TAG, "Launch denied by Kernel: " + reason);
                    if (callback != null) callback.onFailed(reason);
                }
            });
            
            if (!initiated) {
                handler.removeCallbacks(timeoutTask);
                Logger.e(TAG, "VirtualContainer rejected launch request.");
                if (callback != null) callback.onFailed("Engine init failure.");
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
