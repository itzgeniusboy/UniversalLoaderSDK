package com.onecore.sdk.utils;

import android.util.Log;

/**
 * Simple logging utility with tag and level control.
 */
public class Logger {
    private static final String PREFIX = "[OneCore] ";
    private static boolean debugEnabled = true;

    public static void init(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void d(String tag, String msg) {
        if (debugEnabled) Log.d(tag, PREFIX + msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, PREFIX + msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, PREFIX + msg, tr);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, PREFIX + msg);
    }

    public static void v(String tag, String msg) {
        if (debugEnabled) Log.v(tag, PREFIX + msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, PREFIX + msg);
    }
}
