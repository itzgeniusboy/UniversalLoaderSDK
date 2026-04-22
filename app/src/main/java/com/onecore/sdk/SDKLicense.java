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

    public void init(Context context, String customerKey) {
        this.context = context.getApplicationContext();
        this.customerKey = customerKey;
        loadFromCache();
        
        // Time Tampering Check
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0);
        if (LicenseProtector.isTimeTampered(lastCheck)) {
            SecurityManager.handleViolation("Device time tampered");
        }
        
        if (shouldCheckServer()) {
            verifyWithServer();
        } else {
            checkLocalExpiry();
        }
    }

    public boolean isLicensed() {
        // Multiple Verification Points
        return LicenseProtector.checkLicenseIntegrity(isLicensed);
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public long getDaysLeft() {
        if (expiryDate == null) return 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date expiry = sdf.parse(expiryDate);
            Date now = new Date();
            long diff = expiry.getTime() - now.getTime();
            return diff / (24 * 60 * 60 * 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    private void loadFromCache() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.expiryDate = prefs.getString(KEY_EXPIRY, null);
        this.isLicensed = prefs.getBoolean(KEY_IS_VALID, false);
    }

    private boolean shouldCheckServer() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0);
        return (System.currentTimeMillis() - lastCheck) > CACHE_DURATION;
    }

    public void setVerificationCallback(VerificationCallback callback) {
        this.pendingCallback = callback;
        // If already checked, notify immediately
        if (isLicensed) {
            callback.onResult(true, expiryDate, null);
        }
    }

    private void verifyWithServer() {
        executor.execute(() -> {
            try {
                String devId = LicenseProtector.getDeviceId(context);
                URL url = new URL("https://darkdevel.dynamicflash.xyz/connect?key=" + customerKey + "&hwid=" + devId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                     mainHandler.post(() -> {
                         if (pendingCallback != null) pendingCallback.onResult(false, null, "Server Error: " + responseCode);
                     });
                     return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String result = response.toString().toLowerCase();
                boolean valid = false;
                String expiry = "2099-12-31";

                if (result.startsWith("{")) {
                    JSONObject json = new JSONObject(result);
                    valid = json.optBoolean("valid", result.contains("success"));
                    expiry = json.optString("expiry", expiry);
                } else {
                    valid = result.contains("true") || result.contains("success") || result.contains("valid") || result.contains("1");
                }

                boolean finalValid = valid;
                String finalExpiry = expiry;
                mainHandler.post(() -> {
                    updateStatus(finalValid, finalExpiry);
                    if (pendingCallback != null) {
                        pendingCallback.onResult(finalValid, finalExpiry, finalValid ? null : "Key Rejected by Server");
                    }
                });
                
            } catch (Exception e) {
                Logger.e(TAG, "Server verification failed: " + e.getMessage());
                mainHandler.post(() -> {
                    checkLocalExpiry();
                    if (pendingCallback != null) {
                        if (isLicensed) pendingCallback.onResult(true, expiryDate, null);
                        else pendingCallback.onResult(false, null, "Connection Error: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void updateStatus(boolean valid, String expiry) {
        this.isLicensed = valid;
        this.expiryDate = expiry;
        
        checkLocalExpiry();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_IS_VALID, isLicensed)
            .putString(KEY_EXPIRY, expiryDate)
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .apply();
            
        if (!isLicensed) {
            showExpiryDialog();
        }
    }

    private void checkLocalExpiry() {
        if (expiryDate == null) {
            isLicensed = false;
            return;
        }
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date expiry = sdf.parse(expiryDate);
            Date now = new Date();
            
            if (now.after(expiry)) {
                isLicensed = false;
            }
        } catch (Exception e) {
            isLicensed = false;
        }
    }

    public void showExpiryDialog() {
        if (context instanceof Activity) {
            ExpiryDialog.show((Activity) context, expiryDate);
        } else {
            Logger.e(TAG, "Cannot show dialog: Context is not an Activity");
        }
    }
}
