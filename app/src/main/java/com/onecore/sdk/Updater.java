package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.onecore.sdk.utils.Logger;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Real HTTP-based auto updater for OneCore SDK Engine.
 */
public class Updater {
    private static final String TAG = "Updater";
    private static Updater instance;
    private static final String VERSION = "1.0.4";

    private Updater() {}

    public static synchronized Updater getInstance() {
        if (instance == null) {
            instance = new Updater();
        }
        return instance;
    }

    public void checkForUpdates(final Context context) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        new Thread(() -> {
            try {
                URL url = new URL("https://api.onecore.sdk/v1/version");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    Scanner scanner = new Scanner(in).useDelimiter("\\A");
                    String latestVersion = scanner.hasNext() ? scanner.next() : "";
                    
                    if (!VERSION.equals(latestVersion)) {
                        Logger.i(TAG, "New version available: " + latestVersion);
                        // Trigger UI update prompt
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Logger.e(TAG, "Update check failed", e);
            }
        }).start();
    }

    public String getCurrentVersion() {
        return VERSION;
    }

    public void downloadUpdate(Context context, String downloadUrl) {
        Logger.d(TAG, "Downloading update: " + downloadUrl);
        // Real implementation would use DownloadManager or custom stream writer
    }

    public void installUpdate(Context context, String path) {
        try {
            java.io.File file = new java.io.File(path);
            
            // 1. Verify Library Integrity (Checksum)
            // String expectedHash = "9a3f..."; // Obtained from server during version check
            // if (!LibraryProtector.verifyChecksum(file, expectedHash)) {
            //     SecurityManager.handleViolation("Library Integrity Check Failed");
            //     return;
            // }

            // 2. Decrypt in memory (for .dex or .so loading)
            // byte[] encrypted = readAllBytes(file);
            // byte[] decrypted = LibraryProtector.decryptLibrary(encrypted);
            // Logger.d(TAG, "Library decrypted successfully in memory.");
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse("file://" + path), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.e(TAG, "Installation failed", e);
        }
    }
}
