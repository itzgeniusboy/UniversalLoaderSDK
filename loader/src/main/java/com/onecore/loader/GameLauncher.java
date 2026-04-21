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
        Logger.i(TAG, "Initiating Secure Launch Sequence...");
        
        // 1. Launch Cloned App
        VirtualContainer.getInstance().launch(context, TARGET_PKG);
        
        if (callback != null) callback.onProgress("Initializing Sandbox Environment...");

        // 2. Start Async Monitoring after a short delay
        new Thread(() -> {
            try {
                // IMPORTANT: Give the SandboxActivity 3 seconds to fork the process
                Logger.d(TAG, "Waiting 3s for sandbox initialization...");
                Thread.sleep(3000); 
                
                boolean found = false;
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                final String clonedPkg = "com.onecore.cloned.imobile";
                
                Logger.i(TAG, "Starting PID detection loop (30s timeout)...");
                
                for (int i = 0; i < DETECTION_TIMEOUT_SEC; i++) {
                    try {
                        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                        
                        if (processes != null) {
                            for (ActivityManager.RunningAppProcessInfo info : processes) {
                                String procName = info.processName;
                                // Check process name (some systems report the clone pkg or host pkg)
                                boolean isMatch = procName.endsWith(":sandbox") || 
                                                procName.equals(TARGET_PKG) || 
                                                procName.equals(clonedPkg);
                                
                                // Robust fallback: Check pkgList for isolated processes (Standard on Android 11+)
                                if (!isMatch && info.pkgList != null) {
                                    for (String pkg : info.pkgList) {
                                        if (pkg.equals(TARGET_PKG) || pkg.equals("com.pubg.bgmi") || pkg.equals(clonedPkg)) {
                                            isMatch = true;
                                            break;
                                        }
                                    }
                                }

                                if (isMatch) {
                                    int pid = info.pid;
                                    Logger.i(TAG, "✅ Target Process Found! PID: " + pid + " (" + procName + ")");
                                    
                                    // CRITICAL: Call injection AFTER PID is detected
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
                        
                        int currentTick = i + 1;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (callback != null) callback.onProgress("Monitoring Game... (" + currentTick + "/30)");
                        });

                        Thread.sleep(1000); // 1 second intervals

                    } catch (Exception e) {
                        Logger.e(TAG, "Monitor cycle error", e);
                    }
                }

                if (!found) {
                    Logger.e(TAG, "❌ Detection Timeout: Sandbox process not detected.");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (callback != null) callback.onFailed("Detection Timeout - Please restart");
                    });
                    
                    // Log current tasks for debug
                    List<ActivityManager.RunningAppProcessInfo> allProcs = am.getRunningAppProcesses();
                    if (allProcs != null) {
                        StringBuilder sb = new StringBuilder("Running Processes Log:\n");
                        for (ActivityManager.RunningAppProcessInfo pi : allProcs) {
                            sb.append(" -> ").append(pi.processName).append(" (").append(pi.pid).append(")\n");
                        }
                        Logger.w(TAG, sb.toString());
                    }
                }
            } catch (InterruptedException e) {
                Logger.e(TAG, "Monitoring thread interrupted", e);
            }
        }).start();
    }
}
