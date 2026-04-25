package com.onecore.sdk.core;

import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Captures and logs crashes within the virtual environment.
 */
public class OneCoreCrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "OneCore-Crash";
    private final Thread.UncaughtExceptionHandler mBase;

    public OneCoreCrashHandler(Thread.UncaughtExceptionHandler base) {
        this.mBase = base;
    }

    public static void install() {
        Log.i(TAG, "OneCore-DEBUG: Installing Virtual Crash Handler...");
        Thread.setDefaultUncaughtExceptionHandler(new OneCoreCrashHandler(Thread.getDefaultUncaughtExceptionHandler()));
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        
        Log.e(TAG, "!!! ONECORE GUEST CRASH DETECTED !!!");
        Log.e(TAG, "Thread: " + t.getName());
        Log.e(TAG, "Stacktrace: \n" + sw.toString());
        
        // In a complex engine, we could prevent host app from crashing here
        // or relaunch the virtual environment.
        
        if (mBase != null) {
            mBase.uncaughtException(t, e);
        }
    }
}
