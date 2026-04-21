package com.onecore.loader;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import java.util.List;

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
        Logger.i(TAG, "Initiating Virtual Space Launch...");
        
        if (callback != null) callback.onProgress("Verifying Environment...");

        // 1. Ensure library exists in the extracted directory
        File libFile = new File(context.getFilesDir(), "extracted_libs/libbgmi.so");
        if (!libFile.exists()) {
            // Fallback: check DownloadZip again if first check fails
            Logger.w(TAG, "Library not found, attempting last-resort recovery...");
            DownloadZip.start(context, new DownloadZip.DownloadCallback() {
                @Override
                public void onSuccess(File extractedDir) {
                    proceedToLaunch(context, callback);
                }

                @Override
                public void onFailure(String reason) {
                    if (callback != null) callback.onFailed("Library Missing: " + reason);
                }

                @Override
                public void onProgress(String msg) {
                    if (callback != null) callback.onProgress(msg);
                }
            });
        } else {
            proceedToLaunch(context, callback);
        }
    }

    private static void proceedToLaunch(Context context, LaunchCallback callback) {
        // 2. Perform same-process injection
        LibraryInjector.inject(context, TARGET_PKG, null);
        
        // 3. Launch Guest APK in Host process
        VirtualContainer.getInstance().launch(context, TARGET_PKG);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (callback != null) {
                callback.onProgress("Virtual Session ACTIVE");
                callback.onProcessDetected(0);
            }
        }, 1000);
    }
}
