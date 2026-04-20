package com.onecore.sdk;

import android.content.Context;
import android.os.Process;
import com.onecore.sdk.utils.Logger;

/**
 * Main Security Orchestrator for OneCore SDK Engine.
 * Initializes all protection layers and handles violations.
 */
public class SecurityManager {
    private static final String TAG = "SecurityManager";

    public static void init(Context context) {
        Logger.i(TAG, "Initializing OneCore Security Shield...");
        
        // Run initial checks
        AntiDump.checkSecurity();
        AntiReverse.verifyIntegrity(context);
        
        // Start periodic security verification
        startSecurityThread(context);
    }

    private static void startSecurityThread(Context context) {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30000); // Check every 30 seconds
                    AntiDump.checkSecurity();
                    
                    // Root/Emulator detection (re-use AntiDetect)
                    if (AntiDetect.getInstance().isDebuggerAttached()) {
                        handleViolation("Active Debugger Detected");
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    /**
     * Handles any security violation by alerting and exiting.
     */
    public static void handleViolation(String reason) {
        Logger.e(TAG, "CRITICAL SECURITY VIOLATION: " + reason);
        
        // In a production app, you might show a dialog before exiting.
        // For stealth, we just crash or exit.
        
        System.exit(0);
        Process.killProcess(Process.myPid());
    }
}
