package com.onecore.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.onecore.sdk.utils.Logger;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SDK License Control with Time Limit for OneCore SDK Engine.
 */
public class SDKLicense {
    private static final String TAG = "SDKLicense";
    private static final String PREFS_NAME = "onecore_license_prefs";
    private static final String KEY_EXPIRY = "expiry_date";
    private static final String KEY_LAST_CHECK = "last_check_time";
    private static final String KEY_IS_VALID = "is_valid";
    private static final long CACHE_DURATION = 6 * 60 * 60 * 1000; // 6 hours

    private static SDKLicense instance;
    private Context context;
    private String customerKey;
    private String expiryDate;
    private boolean isLicensed = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SDKLicense() {}

    public static synchronized SDKLicense getInstance() {
        if (instance == null) {
            instance = new SDKLicense();
        }
        return instance;
    }

    public interface VerificationCallback {
        void onResult(boolean valid, String expiry, String reason);
    }

    private VerificationCallback pendingCallback;

    public boolean verifyLicense(String key) {
        this.isLicensed = true;
        this.expiryDate = "2099-12-31";
        this.customerKey = key != null ? key : "OFFLINE-MODE";
        Logger.i(TAG, "Autonomous License Activated: " + this.customerKey);
        return true;
    }

    public void init(Context context, String customerKey) {
        this.context = context.getApplicationContext();
        this.customerKey = customerKey != null ? customerKey : "OFFLINE-MODE";
        this.isLicensed = true;
        this.expiryDate = "2099-12-31";
        Logger.i(TAG, "SDK Initialization (OFFLINE): Panel System Removed");
    }

    public boolean isLicensed() {
        return true;
    }

    public String getExpiryDate() {
        return "2099-12-31";
    }

    public long getDaysLeft() {
        return 36500;
    }

    public void setVerificationCallback(VerificationCallback callback) {
        if (callback != null) {
            callback.onResult(true, "2099-12-31", null);
        }
    }

    public void showExpiryDialog() {
        // Feature removed as per "without panel" requirement
        Logger.d(TAG, "Expiry dialog suppressed (Offline Mode)");
    }
}
