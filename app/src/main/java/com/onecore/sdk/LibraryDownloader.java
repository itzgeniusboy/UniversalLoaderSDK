package com.onecore.sdk;

import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Library Downloader for OneCore SDK Engine.
 * Downloads .dex and .so files from remote servers (GitHub/CDN).
 */
public class LibraryDownloader {
    private static final String TAG = "LibraryDownloader";
    private static final String LIB_DIR = "/data/data/com.onecore/files/libraries/";
    private static LibraryDownloader instance;

    private LibraryDownloader() {
        File dir = new File(LIB_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    public static synchronized LibraryDownloader getInstance() {
        if (instance == null) {
            instance = new LibraryDownloader();
        }
        return instance;
    }

    public interface DownloadCallback {
        void onSuccess(File file);
        void onFailure(Exception e);
    }

    public void downloadLibrary(String downloadUrl, String filename, String expectedMd5, DownloadCallback callback) {
        new Thread(() -> {
            try {
                Logger.d(TAG, "Starting download: " + downloadUrl);
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.connect();

                if (conn.getResponseCode() != 200) {
                    throw new Exception("Server returned HTTP " + conn.getResponseCode());
                }

                File outputFile = new File(LIB_DIR, filename);
                try (InputStream is = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream fos = new FileOutputStream(outputFile)) {
                    
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }

                if (expectedMd5 != null && !verifyMd5(outputFile, expectedMd5)) {
                    outputFile.delete();
                    throw new Exception("MD5 Checksum Mismatch");
                }

                Logger.i(TAG, "Library downloaded successfully: " + filename);
                if (callback != null) callback.onSuccess(outputFile);

            } catch (Exception e) {
                Logger.e(TAG, "Download failed: " + e.getMessage());
                if (callback != null) callback.onFailure(e);
            }
        }).start();
    }

    private boolean verifyMd5(File file, String expectedMd5) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, len);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().equalsIgnoreCase(expectedMd5);
        } catch (Exception e) {
            return false;
        }
    }

    public File getLibrary(String filename) {
        File file = new File(LIB_DIR, filename);
        return file.exists() ? file : null;
    }
}
