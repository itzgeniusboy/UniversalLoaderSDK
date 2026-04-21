package com.onecore.loader;

import android.app.Application;
import com.onecore.sdk.OneCoreSDK;
import com.onecore.sdk.utils.Logger;

/**
 * Main Application class for OneCore Loader.
 * Mandatory initialization point for the Virtualization Engine.
 */
public class BoxApplication extends Application {
    private static final String TAG = "BoxApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        
        try {
            // 1. Critical Initialization (Must be before any Activity starts)
            // Using valid production license key
            OneCoreSDK.init(this, "ONECORE-PREMIUM-X782-99");
            
            // 2. Load Core Engine Hooks
            OneCoreSDK.install();
            
            Logger.i(TAG, "SUCCESS: Virtualization Core and Hooks initialized in Application process.");
        } catch (Exception e) {
            Logger.e(TAG, "FATAL: SDK Initialization failed. Application may not function correctly.", e);
        }
    }
}
