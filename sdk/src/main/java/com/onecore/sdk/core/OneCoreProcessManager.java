package com.onecore.sdk.core;

import android.os.Process;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import com.onecore.sdk.utils.ReflectionHelper;

/**
 * Manages virtual process assignment for guest applications.
 */
public class OneCoreProcessManager {
    private static final String TAG = "OneCore-Process";
    private static final Map<String, Integer> mPackageProcessMap = new HashMap<>();
    
    // Assigns a guest package to a specific stub process index
    public static int getProcessIndex(String packageName) {
        if (!mPackageProcessMap.containsKey(packageName)) {
            // Simple round-robin or first available assignment
            int index = (mPackageProcessMap.size() % 2) + 1; // We have P1 and P2
            mPackageProcessMap.put(packageName, index);
        }
        return mPackageProcessMap.get(packageName);
    }
    
    public static void spoofProcessName(String targetProcessName) {
        SafeExecutionManager.run("Process Spoof", () -> {
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Object activityThread = ReflectionHelper.invokeMethod(activityThreadClass, "currentActivityThread");
                if (activityThread != null) {
                    ReflectionHelper.setFieldValue(activityThread, targetProcessName, "mProcessName");
                }
                
                // Also spoof for AppGlobals
                ReflectionHelper.setFieldValue(activityThreadClass, targetProcessName, "sCurrentProcessName");
                
                Log.i(TAG, "Process name spoofed to: " + targetProcessName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to spoof process name", e);
            }
        });
    }

    public static String getProcessName() {
        // Return current process name (simulated check)
        return "virtual_process_" + Process.myPid();
    }
}
