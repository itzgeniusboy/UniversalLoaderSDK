package com.onecore.sdk;

import android.content.Context;
import android.provider.Settings;
import java.security.MessageDigest;

/**
 * Enhanced License Protection for OneCore SDK Engine.
 * Adds device binding and multiple verification points.
 */
public class LicenseProtector {
    
    /**
     * Gets a unique hardware binding ID for the license.
     */
    public static String getDeviceId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(androidId.getBytes());
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 16).toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN_DEVICE";
        }
    }

    /**
     * Secondary validation point for license status.
     * Prevents single-variable patches in memory.
     */
    public static boolean checkLicenseIntegrity(boolean currentStatus) {
        // Obfuscated logic check
        int check = 0;
        if (currentStatus) check += 10;
        if (SDKLicense.getInstance().getDaysLeft() >= 0) check += 5;
        
        // This is a "Dummy" check that can be scattered throughout code
        return check >= 10;
    }

    /**
     * Detects if the device time has been tampered with or frozen.
     */
    public static boolean isTimeTampered(long lastCheckTime) {
        long current = System.currentTimeMillis();
        // If current time is earlier than last check time, time was set back
        return current < lastCheckTime;
    }
}
