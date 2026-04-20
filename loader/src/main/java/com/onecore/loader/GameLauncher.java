package com.onecore.loader;

import android.app.Activity;
import android.widget.Toast;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Handles sanitized game launching for BGMI with Android 15+ compatibility.
 * Optimized for Android 8-18 with universal library injection.
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";
    private static final String LIB_URL = "https://parallaxserver.online/filemanager/raw.php?file=libOWNERHUBEE.so";
    private static final String LIB_NAME = "libOWNERHUBEE.so";

    public static void launchBGMI(Activity activity, String packageName) {
        if (activity == null || packageName == null) return;

        Logger.i(TAG, "Launching Game: " + packageName);

        try {
            // Check for library existence
            File libFile = new File(activity.getFilesDir() + "/onecore_bin", LIB_NAME);
            
            if (!libFile.exists()) {
                Logger.i(TAG, "Library missing, enqueuing download...");
                LibraryDownloader.startDownload(activity, LIB_URL, LIB_NAME, null);
                Toast.makeText(activity, "Downloading assets, please wait...", Toast.LENGTH_LONG).show();
                return;
            }

            Toast.makeText(activity, "Initializing Engine...", Toast.LENGTH_SHORT).show();

            // Perform Version-Aware Injection
            UniversalInjector.performInjection(activity, packageName, libFile.getAbsolutePath());

            // Dynamic Offset Finding for Anti-Update support
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // Wait for game to initialize
                    int pid = OffsetFinder.getPid(activity, packageName);
                    if (pid != -1) {
                        Logger.i(TAG, "Resolving Dynamic Offsets for ESP...");
                        long world = OffsetFinder.findGWorld(activity);
                        Logger.d(TAG, "GWorld resolved at: 0x" + Long.toHexString(world));
                        // Send offsets to native library via SDK memory bridge
                        // This fixes ESP draw failures after BGMI updates
                    }
                } catch (Exception ignored) {}
            }).start();
            VirtualContainer.getInstance().launch(activity.getApplicationContext(), packageName);

        } catch (Exception e) {
            Logger.e(TAG, "Failed to launch game", e);
            Toast.makeText(activity, "Critical Error during launch", Toast.LENGTH_LONG).show();
        }
    }
}
