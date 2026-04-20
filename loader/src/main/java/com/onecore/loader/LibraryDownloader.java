package com.onecore.loader;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.annotation.NonNull;
import com.onecore.sdk.utils.Logger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Background Library Downloader using WorkManager for "No Lag" execution.
 */
public class LibraryDownloader {
    private static final String TAG = "LibraryDownloader";

    public static void enqueueDownload(Context context, String url, String filename) {
        Data inputData = new Data.Builder()
                .putString("url", url)
                .putString("filename", filename)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest downloadRequest = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueue(downloadRequest);
        Logger.d(TAG, "Download enqueued: " + filename);
    }

    public static class DownloadWorker extends Worker {
        public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            String urlStr = getInputData().getString("url");
            String filename = getInputData().getString("filename");

            if (urlStr == null || filename == null) return Result.failure();

            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000); // 10s timeout
                conn.setReadTimeout(10000);
                conn.connect();

                File dir = new File(getApplicationContext().getFilesDir(), "libraries");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, filename);

                try (InputStream is = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }
                
                Logger.i(TAG, "Library downloaded successfully to: " + file.getAbsolutePath());
                return Result.success();
            } catch (Exception e) {
                Logger.e(TAG, "Library download failed", e);
                return Result.retry();
            }
        }
    }
}
