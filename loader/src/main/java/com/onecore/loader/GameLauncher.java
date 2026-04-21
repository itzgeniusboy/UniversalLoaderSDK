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
        Logger.i(TAG, "Initiating Secure Launch...");
        
        // 1. Launch Cloned App
        VirtualContainer.getInstance().launch(context, TARGET_PKG);
        
        if (callback != null) callback.onProgress("Waiting for Game Process...");

        // 2. Start Async Monitoring
        new Thread(() -> {
            boolean found = false;
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            
            for (int i = 0; i < DETECTION_TIMEOUT_SEC; i++) {
                try {
                    Thread.sleep(1000);
                    List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                    
                    if (processes != null) {
                        for (ActivityManager.RunningAppProcessInfo info : processes) {
                            // Check process name ending with :sandbox (host)
                            // OR check the actual pkgList (Android 11 way)
                            boolean isMatch = info.processName.endsWith(":sandbox");
                            
                            if (!isMatch && info.pkgList != null) {
                                for (String pkg : info.pkgList) {
                                    if (pkg.equals(TARGET_PKG) || pkg.equals("com.pubg.bgmi")) {
                                        isMatch = true;
                                        break;
                                    }
                                }
                            }

                            if (isMatch) {
                                int pid = info.pid;
                                Logger.i(TAG, "Target PID found: " + pid);
                                
                                // Call injection
                                LibraryInjector.inject(context, TARGET_PKG, pid, null);
                                
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (callback != null) callback.onProcessDetected(pid);
                                });
                                found = true;
                                break;
                            }
                        }
                    }
                    
                    if (found) break;
                    
                    int currentStep = i + 1;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (callback != null) callback.onProgress("Monitoring: (" + currentStep + "/30)");
                    });

                } catch (Exception e) {
                    Logger.e(TAG, "Monitoring error", e);
                }
            }

            if (!found) {
                Logger.e(TAG, "Failed to detect PID after 30 seconds.");
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onFailed("PID Detection Timeout");
                });
            }
        }).start();
    }
}
