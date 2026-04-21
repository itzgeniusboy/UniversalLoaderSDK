package com.onecore.sdk.injector;

import android.app.ActivityManager;
import android.content.Context;
import com.onecore.sdk.utils.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class NativeInjector {
    private static final String TAG = "NativeInjector";

    public static int findPid(Context context, String targetPackage) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        Logger.i(TAG, "Starting PID detection for: " + targetPackage + " (40 retries)");
        
        for (int i = 0; i < 40; i++) {
            List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
            if (runningProcesses != null) {
                for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                    // Fix: Use .contains() instead of .equals() to catch sandboxed variants
                    if (processInfo.processName.contains(targetPackage) || processInfo.processName.contains(":sandbox")) {
                        Logger.i(TAG, "Found PID via ActivityManager: " + processInfo.pid);
                        return processInfo.pid;
                    }
                    if (processInfo.pkgList != null) {
                        for (String pkg : processInfo.pkgList) {
                            if (pkg.contains(targetPackage)) {
                                Logger.i(TAG, "Found PID via pkgList match: " + processInfo.pid);
                                return processInfo.pid;
                            }
                        }
                    }
                }
            }
            
            // Fallback: Use ps command if ActivityManager fails to return the process
            int psPid = findPidViaPs(targetPackage);
            if (psPid != -1) {
                Logger.i(TAG, "Found PID via ps fallback: " + psPid);
                return psPid;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }
        Logger.e(TAG, "FAILED (Detection Timeout - Please restart)");
        return -1;
    }

    private static int findPidViaPs(String targetPackage) {
        try {
            Process process = Runtime.getRuntime().exec("ps -A");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(targetPackage)) {
                    String[] parts = line.split("\\s+");
                    // Filter out PID from ps output
                    for (String part : parts) {
                        if (part.matches("\\d+")) {
                            return Integer.parseInt(part);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "ps fallback failed", e);
        }
        return -1;
    }

    public static void inject(Context context, int pid, String libPath) {
        if (pid <= 0) return;
        Logger.i(TAG, "Injecting " + libPath + " into PID " + pid);
        // Call to the actual ptrace bridge
        com.onecore.sdk.NativeInjector.injectSo(pid, libPath);
    }
}
