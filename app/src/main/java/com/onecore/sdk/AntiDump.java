package com.onecore.sdk;

import android.os.Debug;
import com.onecore.sdk.utils.Logger;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Memory Dump Protection for OneCore SDK Engine.
 * Detects ptrace, gdb, and memory reading attempts.
 */
public class AntiDump {
    private static final String TAG = "AntiDump";

    public static void checkSecurity() {
        if (isDebuggerAttached() || isTraced() || isGdbDetected()) {
            SecurityManager.handleViolation("Dumping/Debugging detected");
        }
    }

    private static boolean isDebuggerAttached() {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
    }

    /**
     * Detects if the process is being traced via ptrace (e.g., by a debugger or dumper).
     */
    private static boolean isTraced() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.toLowerCase().contains("tracerpid")) {
                    int tracerPid = Integer.parseInt(line.split(":")[1].trim());
                    if (tracerPid != 0) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isGdbDetected() {
        // Basic check for gdb or common debuggers in process list
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            String line = br.readLine();
            if (line != null && (line.contains("gdb") || line.contains("lldb"))) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Clears sensitive arrays from memory.
     */
    public static void clearArray(byte[] array) {
        if (array == null) return;
        for (int i = 0; i < array.length; i++) {
            array[i] = 0;
        }
    }
}
