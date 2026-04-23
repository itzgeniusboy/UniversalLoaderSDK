package com.onecore.loader;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.onecore.sdk.utils.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Automates GitHub Release checking and APK installation.
 */
public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/itzgeniusboy/UniversalLoaderSDK/releases/latest";
    private final Context context;
    private final OkHttpClient client;
    private final String currentVersion;

    public UpdateChecker(Context context) {
        this.context = context;
        this.client = new OkHttpClient();
        this.currentVersion = getAppVersionName();
    }

    private String getAppVersionName() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    public void checkForUpdates(boolean manual) {
        Logger.i(TAG, "Checking for updates... Current version: " + currentVersion);
        
        Request request = new Request.Builder()
                .url(GITHUB_API_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.e(TAG, "GitHub API fetch failed", e);
                if (manual) {
                    showToast("Update check failed. Check Internet.");
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (manual) showToast("Server error: " + response.code());
                    return;
                }

                try {
                    String jsonData = response.body().string();
                    JSONObject jsonObject = new JSONObject(jsonData);
                    String latestTag = jsonObject.getString("tag_name");
                    JSONArray assets = jsonObject.getJSONArray("assets");
                    
                    String downloadUrl = null;
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url");
                            break;
                        }
                    }

                    if (isNewerVersion(currentVersion, latestTag)) {
                        String finalDownloadUrl = downloadUrl;
                        new Handler(Looper.getMainLooper()).post(() -> showUpdateDialog(latestTag, finalDownloadUrl));
                    } else if (manual) {
                        showToast("You are on the latest version: " + currentVersion);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "JSON parsing error", e);
                }
            }
        });
    }

    private boolean isNewerVersion(String current, String latest) {
        // Strip 'v' if present (e.g., v1.0.5 -> 1.0.5)
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
        return false;
    }

    private void showUpdateDialog(String version, String downloadUrl) {
        new AlertDialog.Builder(context)
                .setTitle("Update Available \uD83D\uDE80")
                .setMessage("A new version (" + version + ") is available. Would you like to update now?\n\nCurrent: " + currentVersion)
                .setPositiveButton("UPDATE NOW", (dialog, which) -> {
                    if (downloadUrl != null) {
                        downloadAndInstall(downloadUrl, version);
                    } else {
                        Toast.makeText(context, "No APK asset found in release.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("LATER", null)
                .setCancelable(true)
                .show();
    }

    private void downloadAndInstall(String url, String version) {
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_LONG).show();
        
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "OneCoreLoader_" + version + ".apk");
        if (file.exists()) file.delete();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Updating OneCore Loader")
                .setDescription("Downloading version " + version)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = dm.enqueue(request);

        // Receiver to handle install after download
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context c, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    installApk(file);
                    context.unregisterReceiver(this);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private void installApk(File file) {
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
    }

    private void showToast(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }
}
