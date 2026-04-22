package com.onecore.loader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.onecore.sdk.OneCoreSDK;
import com.onecore.sdk.utils.Logger;

/**
 * Manages the sequential flow of SDK Initialization -> License Check -> Core Installation.
 */
public class LaunchManager {
    private static final String TAG = "LaunchManager";
    private static LaunchManager instance;
    private final Context context;
    private final Handler mainHandler;

    public interface LaunchListener {
        void onProgress(int progress, String message);
        void onReady();
        void onFailed(String reason);
    }

    private LaunchManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized LaunchManager getInstance(Context context) {
        if (instance == null) {
            instance = new LaunchManager(context);
        }
        return instance;
    }

    public void start(String licenseKey, LaunchListener listener) {
        Logger.i(TAG, "Starting Secure Launch Sequence...");
        
        // 1. Initialize SDK
        if (listener != null) listener.onProgress(10, "Initializing Engine...");
        OneCoreSDK.init(context, licenseKey);
        
        // 2. Wait for License Verification
        com.onecore.sdk.SDKLicense.getInstance().setVerificationCallback((valid, expiry, reason) -> {
            if (!valid) {
                if (listener != null) listener.onFailed("License Error: " + reason);
                return;
            }

            if (listener != null) listener.onProgress(30, "License Verified");

            // 3. Perform Real Installation with Sequential Feedback
            OneCoreSDK.install(new OneCoreSDK.InstallCallback() {
                @Override
                public void onProgress(int progress, String message) {
                    mainHandler.post(() -> {
                        if (listener != null) listener.onProgress(progress, message);
                    });
                }

                @Override
                public void onSuccess() {
                    mainHandler.post(() -> {
                        Logger.i(TAG, "Sequential Launch Successful.");
                        if (listener != null) listener.onReady();
                    });
                }

                @Override
                public void onFailure(String reason) {
                    mainHandler.post(() -> {
                        Logger.e(TAG, "Launch Aborted: " + reason);
                        if (listener != null) listener.onFailed(reason);
                    });
                }
            });
        });
    }
}
