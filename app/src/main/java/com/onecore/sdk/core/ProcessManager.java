package com.onecore.sdk.core;

import android.app.ActivityManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks virtual processes inside the sandbox.
 */
public class ProcessManager {
    private static final ProcessManager sInstance = new ProcessManager();
    private final List<ActivityManager.RunningAppProcessInfo> mProcesses = new ArrayList<>();

    public static ProcessManager getInstance() {
        return sInstance;
    }

    public List<ActivityManager.RunningAppProcessInfo> getRunningProcesses() {
        // Return spoofed process list to the virtual app
        // Ensure the virtual app only sees itself and 'system'
        return mProcesses;
    }

    public void addProcess(int pid, String processName) {
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        info.pid = pid;
        info.processName = processName;
        mProcesses.add(info);
    }
}
