package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;
import com.onecore.sdk.utils.Logger;

public class LauncherOrchestrator {
    private static final String TAG = "LauncherOrchestrator";

    public static void startGame(Context context, String packageName) {
        Logger.i(TAG, ">>> VIRTUAL GAME START REQUESTED: " + packageName + " <<<");

        if (!isPackageInstalled(context, packageName)) {
            Logger.e(TAG, "Launch Aborted: Package NOT installed locally.");
            Toast.makeText(context, "Game not installed: " + packageName, Toast.LENGTH_LONG).show( );
            return;
        }

        // Force License Bypass for Dev/Testing
        Logger.i(TAG, "License Check: Bypass ACTIVE (System Authorized)");

        try {
            Logger.i(TAG, "Initiating Secure Virtual Handshake...");
            boolean virtualSuccess = VirtualContainer.getInstance().prepareAndLaunch(context, packageName);
            
            if (!virtualSuccess) {
                Logger.e(TAG, "CRITICAL: Virtual Environment Preparation Failed.");
                Logger.w(TAG, "Launch Aborted to protect process integrity (Fallback Blocked).");
                Toast.makeText(context, "Virtual Initialization Error. Aborting to protect accounts.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Logger.e(TAG, "Kernel space failure in Virtual Logic: " + e.getMessage());
            Toast.makeText(context, "Sandbox Crash: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Fallback method REMOVED to prevent accidental real-app launch
    /*
    public static void launchFallback(Context context, String packageName) { ... }
    */

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
