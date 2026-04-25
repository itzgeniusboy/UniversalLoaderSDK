package com.onecore.sdk.core;

import android.util.Log;

/**
 * Wraps critical hook executions in try-catch blocks with fallback support.
 */
public class SafeExecutionManager {
    private static final String TAG = "OneCore-SafeExec";

    public interface HookTask {
        void execute() throws Exception;
    }

    public static void run(String name, HookTask task) {
        try {
            task.execute();
        } catch (Throwable e) {
            Log.e(TAG, "!!! ONECORE-CRITICAL-FAIL: " + name + " failed. !!!", e);
            // In the future, we could trigger a cleanup or alternative hook strategy here
        }
    }
    
    public static <T> T runWithResult(String name, ResultTask<T> task, T fallback) {
        try {
            return task.execute();
        } catch (Throwable e) {
            Log.e(TAG, "!!! ONECORE-FAIL: " + name + " failed, using fallback. !!!", e);
            return fallback;
        }
    }

    public interface ResultTask<T> {
        T execute() throws Exception;
    }
}
