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
        try {
            // Detect installed version
            String targetPkg = PKG_IMOBILE;
            try {
                context.getPackageManager().getPackageInfo(PKG_IMOBILE, 0);
            } catch (Exception e) {
                targetPkg = PKG_BGMI;
            }

            Logger.i(TAG, "Triggering Sandbox Launch for: " + targetPkg);
            if (callback != null) callback.onProgress("Initializing Isolated Engine...");

            // Ensure we use the Virtualization Container
            VirtualContainer.getInstance().launch(context, targetPkg, new VirtualContainer.LaunchCallback() {
                @Override
                public void onLaunchSuccess() {
                    Logger.i(TAG, "Virtual Session Active. Syncing Hooks...");
                    if (callback != null) {
                        callback.onProgress("Launch Success");
                        callback.onProcessDetected(0);
                    }
                }

                @Override
                public void onLaunchFailed(String reason) {
                    Logger.e(TAG, "Sandbox Launch Failed: " + reason);
                    if (callback != null) callback.onFailed(reason);
                }
            });

        } catch (Exception e) {
            Logger.e(TAG, "Fatal Launch Error", e);
            if (callback != null) callback.onFailed(e.getMessage());
        }
    }
}
