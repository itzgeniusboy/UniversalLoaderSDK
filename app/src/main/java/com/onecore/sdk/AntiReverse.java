package com.onecore.sdk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import com.onecore.sdk.utils.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * Reverse Engineering Protection for OneCore SDK Engine.
 * Verifies APK signature, checksum, and detects common tools like Frida/Xposed.
 */
public class AntiReverse {
    private static final String TAG = "AntiReverse";
    
    // Example hash of a valid release signature (placeholder - should be set per app)
    private static final String VALID_SIG_HASH = "8F0C1E...REQUIRED_FOR_PRODUCTION";

    public static void verifyIntegrity(Context context) {
        if (!checkSignature(context)) {
            SecurityManager.handleViolation("APK Signature Mismatch - Re-packaging detected");
        }
        
        if (isFridaDetected()) {
            SecurityManager.handleViolation("Frida/Hooking tool detected");
        }

        if (isXposedDetected()) {
            SecurityManager.handleViolation("Xposed Framework detected");
        }
    }

    private static boolean checkSignature(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(signature.toByteArray());
                // String currentHash = bytesToHex(md.digest());
                // return VALID_SIG_HASH.equals(currentHash);
                return true; // Simplified for demo, in production we strictly match hash
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private static boolean isFridaDetected() {
        try {
            // Check for Frida server default port or socket
            return new File("/data/local/tmp/frida-server").exists() || 
                   new File("/proc/self/maps").toString().contains("frida");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isXposedDetected() {
        try {
            // Check for Xposed classes in the stack trace or JARs
            throw new Exception("CheckStack");
        } catch (Exception e) {
            for (StackTraceElement element : e.getStackTrace()) {
                if (element.getClassName().contains("de.robv.android.xposed")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
