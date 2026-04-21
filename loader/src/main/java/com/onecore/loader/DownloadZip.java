package com.onecore.loader;

import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles downloading and extracting the library ZIP from GitHub Releases.
 */
public class DownloadZip {
    private static final String TAG = "DownloadZip";
    private static final String DOWNLOAD_URL = "https://github.com/itzgeniusboy/OneCoreLoader/releases/download/OneCoreLoader/Saved.zip";

    public interface DownloadCallback {
        void onSuccess(File extractedDir);
        void onFailure(String reason);
        void onProgress(String msg);
    }

    public static void start(Context context, DownloadCallback callback) {
        new Thread(() -> {
            try {
                if (callback != null) callback.onProgress("Connecting to Server...");
                
                URL url = new URL(DOWNLOAD_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                // Handle Redirects (GitHub often redirects to AWS/S3)
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.connect();
                }

                File zipFile = new File(context.getCacheDir(), "Saved.zip");
                if (callback != null) callback.onProgress("Downloading remote assets...");

                try (InputStream is = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream fos = new FileOutputStream(zipFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }

                if (callback != null) callback.onProgress("Processing binaries...");
                File extractDir = new File(context.getFilesDir(), "extracted_libs");
                unzip(zipFile, extractDir);

                Logger.i(TAG, "Download and extraction successful: " + extractDir.getAbsolutePath());
                if (callback != null) callback.onSuccess(extractDir);

            } catch (Exception e) {
                Logger.e(TAG, "Failed to download/extract ZIP", e);
                if (callback != null) callback.onFailure(e.getMessage());
            }
        }).start();
    }

    private static void unzip(File zipFile, File targetDir) throws Exception {
        if (!targetDir.exists()) targetDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
