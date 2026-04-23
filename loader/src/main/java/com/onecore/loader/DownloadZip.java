package com.onecore.loader;

import android.content.Context;
import android.os.AsyncTask;
import com.onecore.sdk.utils.Logger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility to download and extract ZIP assets for the sandbox.
 * Removed auto-update logic; strictly for resource management.
 */
public class DownloadZip {
    private static final String TAG = "DownloadZip";

    public interface DownloadCallback {
        void onSuccess(File extractedDir);
        void onFailure(String reason);
        void onProgress(String message);
    }

    public static void start(Context context, DownloadCallback callback) {
        String url = "https://github.com/itzgeniusboy/OneCoreLoader/releases/download/OneCoreLoader/Saved.zip";
        new DownloadTask(context, callback).execute(url);
    }

    private static class DownloadTask extends AsyncTask<String, String, File> {
        private final Context context;
        private final DownloadCallback callback;
        private String errorReason;

        public DownloadTask(Context context, DownloadCallback callback) {
            this.context = context;
            this.callback = callback;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (callback != null) callback.onProgress(values[0]);
        }

        @Override
        protected File doInBackground(String... params) {
            String urlStr = params[0];
            try {
                publishProgress("Connecting to server...");
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    errorReason = "Server returned: " + conn.getResponseMessage();
                    return null;
                }

                File outputDir = new File(context.getFilesDir(), "sandbox_res");
                if (!outputDir.exists()) outputDir.mkdirs();

                publishProgress("Extracting assets...");
                try (InputStream is = new BufferedInputStream(conn.getInputStream());
                     ZipInputStream zis = new ZipInputStream(is)) {
                    
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        File outFile = new File(outputDir, entry.getName());
                        if (entry.isDirectory()) {
                            outFile.mkdirs();
                        } else {
                            // Ensure parent directories exist
                            File parent = outFile.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();
                            
                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
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
                return outputDir;

            } catch (Exception e) {
                Logger.e(TAG, "Download/Extract failed", e);
                errorReason = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(File result) {
            if (result != null) {
                if (callback != null) callback.onSuccess(result);
            } else {
                if (callback != null) callback.onFailure(errorReason != null ? errorReason : "Unknown error");
            }
        }
    }
}
