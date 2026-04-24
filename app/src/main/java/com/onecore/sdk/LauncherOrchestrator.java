package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;
import com.onecore.sdk.utils.Logger;

public class LauncherOrchestrator {
    private static final String TAG = "LauncherOrchestrator";

    public static void startGame(Context context, String packageName) {
        Logger.i(TAG, ">>> GAME START REQUESTED: " + packageName + " <<<");

        if (!isPackageInstalled(context, packageName)) {
            Logger.e(TAG, "Launch Aborted: Package NOT installed.");
            Toast.makeText(context, "Game not installed: " + packageName, Toast.LENGTH_LONG).show( );
            return;
        }

        // Force License Bypass for Dev/Testing
        Logger.i(TAG, "License Check: Bypass ACTIVE (Dev Mode)");

        try {
            Logger.i(TAG, "Attempting Virtual Launch...");
            boolean virtualSuccess = VirtualContainer.getInstance().prepareAndLaunch(context, packageName);
            
            if (!virtualSuccess) {
                Logger.w(TAG, "Virtual Launch Failed Pre-Check. Triggering Fallback immediately.");
                launchFallback(context, packageName);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Critical failure in Virtual Logic: " + e.getMessage());
            launchFallback(context, packageName);
        }
    }

    public static void launchFallback(Context context, String packageName) {
        Logger.i(TAG, "--- TRIGGERING GUARANTEED FALLBACK LAUNCH ---");
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Logger.i(TAG, "Fallback Success: " + packageName + " launched directly.");
            } else {
                Logger.e(TAG, "Fallback Failed: Intent is NULL for " + packageName);
                Toast.makeText(context, "Unable to resolve launch activity for: " + packageName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Logger.e(TAG, "Fallback CRASH: " + e.getMessage());
            Toast.makeText(context, "System error launching game: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
