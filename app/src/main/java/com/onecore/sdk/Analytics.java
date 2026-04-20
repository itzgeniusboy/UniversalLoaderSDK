package com.onecore.sdk;

import com.onecore.sdk.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles analytics event tracking and crash reporting.
 */
public class Analytics {
    private static final String TAG = "Analytics";
    private static Analytics instance;
    private final Map<String, String> userProperties = new HashMap<>();

    private Analytics() {}

    public static synchronized Analytics getInstance() {
        if (instance == null) {
            instance = new Analytics();
        }
        return instance;
    }

    public void trackEvent(String eventName) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Logger.d(TAG, "Tracking Event: " + eventName);
        // In real app, push to Firebase or custom backend
    }

    public void trackCrash(Throwable throwable) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Logger.e(TAG, "Tracking Crash", throwable);
    }

    public void setUserProperty(String key, String value) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        userProperties.put(key, value);
        Logger.d(TAG, "User Property Set: " + key + "=" + value);
    }

    public void flushAnalytics() {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Logger.d(TAG, "Flushing analytics data...");
    }
}
