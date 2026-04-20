package com.onecore.loader;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;

/**
 * Handles sanitized game launching for BGMI with Android 15 compatibility.
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";

    public static void launchBGMI(Activity activity, String packageName) {
        if (activity == null || packageName == null) return;

        Logger.i(TAG, "Launching Game: " + packageName);

        try {
            // Check for background start restrictions on Android 15
            // Ensure app is in foreground or has permission
            Toast.makeText(activity, "Initializing Engine...", Toast.LENGTH_SHORT).show();

            // Run launch sequence in a controlled manner
            VirtualContainer.getInstance().launch(activity.getApplicationContext(), packageName);

        } catch (Exception e) {
            Logger.e(TAG, "Failed to launch game", e);
            Toast.makeText(activity, "Critical Error during launch", Toast.LENGTH_LONG).show();
        }
    }
}
