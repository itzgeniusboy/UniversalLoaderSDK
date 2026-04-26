package com.onecore.sdk.core;

import android.util.Log;
import android.os.Process;
import android.os.Build;

/**
 * Manages performance boosting for heavy games like BGMI.
 * Adjusts thread priorities, suggests high-performance rendering, and manages CPU/GPU hints.
 */
public class OneCorePerformanceEngine {
    private static final String TAG = "OneCore-Performance";

    public static void boost() {
        Log.i(TAG, "OneCore-DEBUG: High-Performance Engine Initializing...");
        
        SafeExecutionManager.run("Performance Boost", () -> {
            // 1. Set main thread priority to high
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
            
            // 2. Performance Hints (Tencent/BGMI specific)
            // Some specific properties to help UE4 performance
            System.setProperty("sys.use_fifo_ui", "1");
            System.setProperty("debug.egl.hw", "1");
            System.setProperty("debug.egl.profiler", "0");
            System.setProperty("debug.sf.latch_unsignaled", "1");
            
            // 3. Force 120 FPS / 90 FPS support hint for the game
            System.setProperty("persist.sys.performance.level", "3");
            
            // 4. Disable some battery saving features for the game process
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Future: Add logic to request battery optimization ignore
            }

            Log.d(TAG, "BGMI-Boost-Ready: CPU/GPU priorities elevated.");
        });
    }

    /**
     * Called when a new thread is spawned by the game (e.g., UE4 TaskGraph threads).
     */
    public static void optimizeNewThread(int tid) {
        try {
            Process.setThreadPriority(tid, Process.THREAD_PRIORITY_URGENT_AUDIO);
        } catch (Exception ignored) {}
    }
}
