package com.loader.sdk;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.loader.sdk.utils.Logger;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Real HTTP-based auto updater.
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
        new Thread(() -> {
            try {
                URL url = new URL("https://api.loader.sdk/v1/version");
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
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse("file://" + path), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.e(TAG, "Installation failed", e);
        }
    }
}
