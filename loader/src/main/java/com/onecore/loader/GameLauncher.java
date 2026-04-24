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
            boolean installed = true;
            try {
                context.getPackageManager().getPackageInfo(PKG_IMOBILE, 0);
                Logger.d(TAG, "Detected: " + PKG_IMOBILE);
            } catch (Exception e) {
                try {
                    context.getPackageManager().getPackageInfo(PKG_BGMI, 0);
                    originalPkg = PKG_BGMI;
                    Logger.d(TAG, "Detected: " + PKG_BGMI);
                } catch (Exception e2) {
                    installed = false;
                }
            }
            
            if (!installed) {
                Logger.e(TAG, "FATAL: BGMI not installed.");
                if (callback != null) callback.onFailed("BGMI is not installed. Please install the game first.");
                return;
            }
            
            Logger.i(TAG, "Step 1: Identifying virtualization target for " + originalPkg);
            if (callback != null) callback.onProgress("Initializing Isolated Engine...");

            // Ensure we use the Virtualization Container
            Logger.d(TAG, "Handing off to VirtualContainer.launch()...");
            VirtualContainer.getInstance().launch(context, originalPkg, new VirtualContainer.LaunchCallback() {
                @Override
                public void onLaunchSuccess() {
                    Logger.i(TAG, "Step 2: Virtual Sandbox Boot SUCCESS.");
                    if (callback != null) {
                        callback.onProgress("Launch Success");
                        callback.onProcessDetected(0);
                    }
                }

                @Override
                public void onLaunchFailed(String reason) {
                    Logger.e(TAG, "Step 2: Virtual Sandbox Boot FAILED: " + reason);
                    if (callback != null) callback.onFailed(reason);
                }
            });

        } catch (Exception e) {
            Logger.e(TAG, "FATAL ERROR in GameLauncher logic", e);
            if (callback != null) callback.onFailed(e.getMessage());
        }
    }
}
