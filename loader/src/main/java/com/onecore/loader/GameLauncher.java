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
        new Thread(() -> {
            try {
                if (callback != null) callback.onProgress("Initializing Virtual Environment...");

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
                
                // REAL PROGRESS: Using OneCore SDK and VirtualContainer
                com.onecore.sdk.OneCoreSDK.init(context);
                com.onecore.sdk.VirtualContainer container = com.onecore.sdk.VirtualContainer.getInstance();
                
                android.content.pm.ApplicationInfo hostAppInfo = context.getPackageManager().getApplicationInfo(targetPkg, 0);
                String sourcePath = hostAppInfo.sourceDir;
                
                if (callback != null) callback.onProgress("Preparing Virtual Space...");
                boolean installed = container.installApk(context, sourcePath, targetPkg);
                
                if (!installed) {
                    if (callback != null) callback.onFailed("Failed to initialize virtual space");
                    return;
                }

                if (callback != null) callback.onProgress("Launching in Container...");
                
                // Phase 3: Bind Application with correct context
                final String finalPkg = targetPkg;
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        // Launch the main activity via Stub
                        android.content.Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(finalPkg);
                        if (launchIntent != null && launchIntent.getComponent() != null) {
                            String targetActivity = launchIntent.getComponent().getClassName();
                            Log.i(TAG, "Redirecting launch to Stub: " + targetActivity);
                            
                            // Add suggested flags for better reliability
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            
                            // Important: ensure OneCoreSDK is initialized if not already
                            if (!com.onecore.sdk.OneCoreSDK.isInitialized()) {
                                com.onecore.sdk.OneCoreSDK.init(context);
                            }
                            
                            // Instead of direct launch, we move to SandboxActivity which handles stubbing
                            android.content.Intent sandboxIntent = new android.content.Intent();
                            sandboxIntent.setClassName(context.getPackageName(), "com.onecore.sdk.core.SandboxActivity");
                            sandboxIntent.putExtra("target_package", finalPkg);
                            sandboxIntent.putExtra("main_activity", targetActivity);
                            sandboxIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            
                            context.startActivity(sandboxIntent);
                            if (callback != null) callback.onProcessDetected(1); 
                        } else {
                            Log.e(TAG, "Launch intent or component is null for " + finalPkg);
                            if (callback != null) callback.onFailed("Failed to find main activity for " + finalPkg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Critical error during launch dispatch", e);
                        if (callback != null) callback.onFailed("Launch internal error: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in GameLauncher", e);
                if (callback != null) callback.onFailed(e.getMessage());
            }
        }).start();
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
