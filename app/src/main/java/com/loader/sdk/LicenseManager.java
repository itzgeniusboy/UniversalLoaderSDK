package com.loader.sdk;

import android.content.Context;
import android.provider.Settings;
import com.loader.sdk.utils.Logger;
import java.util.Date;

/**
 * Manages SDK licensing, expiry, and device binding.
 */
public class LicenseManager {
    private static final String TAG = "LicenseManager";
    private static LicenseManager instance;
    private String licenseKey;
    private int trialDays = 7;
    private long firstLaunchTime = 0;

    private LicenseManager() {}

    public static synchronized LicenseManager getInstance() {
        if (instance == null) {
            instance = new LicenseManager();
        }
        return instance;
    }

    public void setLicenseKey(String key) {
        this.licenseKey = key;
        Logger.d(TAG, "License key set.");
    }

    public boolean isLicenseValid(Context context) {
        if (licenseKey == null || licenseKey.isEmpty()) {
            return checkTrialStatus();
        }
        // In a real app, this would verify the key with a server
        // and check the device hardware ID (HWID).
        String deviceId = getDeviceId(context);
        return licenseKey.startsWith("VALID_") && licenseKey.contains(deviceId.substring(0, 4));
    }

    private boolean checkTrialStatus() {
        if (firstLaunchTime == 0) {
            firstLaunchTime = System.currentTimeMillis();
        }
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - firstLaunchTime;
        long days = diff / (24 * 60 * 60 * 1000);
        return days <= trialDays;
    }

    public Date getExpiryDate() {
        if (firstLaunchTime == 0) return new Date();
        return new Date(firstLaunchTime + ((long) trialDays * 24 * 60 * 60 * 1000));
    }

    public void setTrialDays(int days) {
        this.trialDays = days;
    }

    private String getDeviceId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
}
