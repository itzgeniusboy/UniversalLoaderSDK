package com.onecore.sdk.utils;

import android.content.Context;
import android.os.Process;
import androidx.annotation.NonNull;

/**
 * Intercepts unhandled exceptions to prevent the app from crashing 
 * and log the error for debugging.
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static CrashHandler instance;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context context;

    private CrashHandler() {}

    public static synchronized CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        Logger.d(TAG, "Crash Handler installed.");
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        Logger.e(TAG, "CRASH DETECTED in thread " + thread.getName(), throwable);
        
        // Custom error handling logic here (e.g. save to file, send to server)
        
        try {
            // Give some time for logging to finish
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore
        }

        // Ideally, we restart the app or just exit gracefully
        if (defaultHandler != null) {
            // Hand over to system or original handler
            defaultHandler.uncaughtException(thread, throwable);
        } else {
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }
}
