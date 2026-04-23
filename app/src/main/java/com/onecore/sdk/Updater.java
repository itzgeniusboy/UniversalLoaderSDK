package com.onecore.sdk;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.os.Handler;

import androidx.core.content.FileProvider;

import com.onecore.sdk.utils.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.io.File;

/**
 * Real GitHub-based auto updater for OneCore SDK Engine.
 * Handles update checking, background downloading, and secure installation.
 */
public class Updater {
    private static final String TAG = "OneCore-Updater";
    private static Updater instance;
    private static final String GITHUB_URL = "https://api.github.com/repos/itzgeniusboy/UniversalLoaderSDK/releases/latest";

    private Updater() {}

    public static synchronized Updater getInstance() {
        if (instance == null) {
            instance = new Updater();
        }
        return instance;
    }

    public void checkForUpdates(final Context context, boolean manual) {
        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    Scanner scanner = new Scanner(in).useDelimiter("\\A");
                    String jsonData = scanner.hasNext() ? scanner.next() : "";
                    
                    JSONObject jsonObject = new JSONObject(jsonData);
                    String latestVersion = jsonObject.getString("tag_name");
                    JSONArray assets = jsonObject.getJSONArray("assets");
                    
                    String downloadUrl = null;
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url");
                            break;
                        }
                    }

                    String currentVersion = getVersionName(context);
                    if (isNewer(currentVersion, latestVersion)) {
                        String finalUrl = downloadUrl;
                        new Handler(Looper.getMainLooper()).post(() -> showUpdatePrompt(context, latestVersion, finalUrl));
                    } else if (manual) {
                        new Handler(Looper.getMainLooper()).post(() -> 
                            android.widget.Toast.makeText(context, "Latest version already installed.", android.widget.Toast.LENGTH_SHORT).show());
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Logger.e(TAG, "Update check failed", e);
            }
        }).start();
    }

    private String getVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    private boolean isNewer(String current, String latest) {
        try {
            String c = current.replaceAll("[^0-9.]", "");
            String l = latest.replaceAll("[^0-9.]", "");
            String[] cParts = c.split("\\.");
            String[] lParts = l.split("\\.");
            int length = Math.max(cParts.length, lParts.length);
            for (int i = 0; i < length; i++) {
                int cV = i < cParts.length ? Integer.parseInt(cParts[i]) : 0;
                int lV = i < lParts.length ? Integer.parseInt(lParts[i]) : 0;
                if (lV > cV) return true;
                if (lV < cV) return false;
            }
        } catch (Exception e) {
            return !current.equals(latest);
        }
        return false;
    }

    private void showUpdatePrompt(Context context, String version, String downloadUrl) {
        new AlertDialog.Builder(context)
                .setTitle("New Version Detected \uD83D\uDE80")
                .setMessage("A superior version (" + version + ") of OneCore SDK is available. Download now?")
                .setPositiveButton("DOWNLOAD", (dialog, which) -> startDownload(context, downloadUrl, version))
                .setNegativeButton("DISMISS", null)
                .show();
    }

    public void startDownload(Context context, String url, String version) {
        Logger.d(TAG, "Background update download initiated: " + url);
        
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "onecore_update_" + version + ".apk");
        if (file.exists()) file.delete();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading OneCore Update")
                .setDescription("Preparing version " + version)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true);

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = dm.enqueue(request);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    new Handler(Looper.getMainLooper()).post(() -> showInstallPrompt(context, file));
                    context.unregisterReceiver(this);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= 34) {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private void showInstallPrompt(Context context, File file) {
        new AlertDialog.Builder(context)
                .setTitle("Download Complete \u2705")
                .setMessage("The update file is ready. Install now?")
                .setPositiveButton("INSTALL", (dialog, which) -> executeInstall(context, file))
                .setNegativeButton("NOT NOW", null)
                .setCancelable(false)
                .show();
    }

    private void executeInstall(Context context, File file) {
        try {
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            } else {
                apkUri = Uri.fromFile(file);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.e(TAG, "Installation failed", e);
        }
    }
}
