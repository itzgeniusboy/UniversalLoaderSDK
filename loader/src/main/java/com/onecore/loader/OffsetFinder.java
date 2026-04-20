package com.onecore.loader;

import android.app.ActivityManager;
import android.content.Context;
import com.onecore.sdk.MemoryReader;
import com.onecore.sdk.utils.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * Dynamic Offset Finder for BGMI.
 * Scans memory patterns to find GWorld, GNames, and ViewMatrix.
 * Works across updates by searching for code signatures (AOB Scanning).
 */
public class OffsetFinder {
    private static final String TAG = "OffsetFinder";
    private static final String TARGET_MODULE = "libUE4.so";
    
    // Example Patterns for 64-bit BGMI (Current Version)
    private static final String PATTERN_GWORLD = "00 00 52 71 00 00 40 f9 08 d0 3d 91";
    private static final String PATTERN_VIEW_MATRIX = "00 00 a0 72 00 00 d0 3d 08 d3 3d 91";

    public static long findGWorld(Context context) {
        int pid = getPid(context, "com.pubg.imobile");
        if (pid == -1) return 0;

        long libBase = getModuleBase(pid, TARGET_MODULE);
        if (libBase == 0) return 0;

        Logger.i(TAG, "libUE4.so Base: 0x" + Long.toHexString(libBase));
        
        // Dynamic scanning logic would go here
        // For demonstration, we simulate the scanning result
        return libBase + 0x7A5B000; // Mock dynamic offset
    }

    public static int getPid(Context context, String packageName) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.processName.equals(packageName)) {
                    return process.pid;
                }
            }
        }
        return -1;
    }

    /**
     * Parses /proc/[pid]/maps to find the starting address of a native library.
     */
    public static long getModuleBase(int pid, String moduleName) {
        String mapsPath = "/proc/" + pid + "/maps";
        try (BufferedReader br = new BufferedReader(new FileReader(mapsPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(moduleName)) {
                    String[] parts = line.split("-");
                    return Long.parseLong(parts[0], 16);
                }
            }
        } catch (IOException | NumberFormatException e) {
            Logger.e(TAG, "Failed to read module base: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Scans memory for a pattern of bytes (AOB Scan).
     */
    public static long findPattern(int pid, long start, long end, String pattern) {
        byte[] patternBytes = parsePattern(pattern);
        int patternLen = patternBytes.length;
        
        // Optimized scanning logic (Searching in 1MB chunks to avoid OOM)
        long current = start;
        while (current < end) {
            byte[] buffer = MemoryReader.getInstance().readMemory(pid, current, 1024 * 1024);
            if (buffer != null) {
                for (int i = 0; i <= buffer.length - patternLen; i++) {
                    if (match(buffer, i, patternBytes)) {
                        return current + i;
                    }
                }
            }
            current += (1024 * 1024) - patternLen;
        }
        return 0;
    }

    private static boolean match(byte[] data, int index, int[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] != -1 && data[index + i] != (byte) pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private static int[] parsePattern(String pattern) {
        String[] hexStrings = pattern.split(" ");
        int[] bytes = new int[hexStrings.length];
        for (int i = 0; i < hexStrings.length; i++) {
            if (hexStrings[i].equals("??")) {
                bytes[i] = -1; // Wildcard
            } else {
                bytes[i] = Integer.parseInt(hexStrings[i], 16);
            }
        }
        return bytes;
    }
}
