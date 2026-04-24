package com.onecore.sdk;

import android.app.Application;
import android.content.Context;
import com.onecore.sdk.core.ActivityThreadHook;
import com.onecore.sdk.utils.Logger;

/**
 * Example Application class showing the correct injection point for OneCore SDK.
 * Use this in your AndroidManifest.xml for the Sandbox process.
 */
public class OneCoreApplication extends Application {
    private static final String TAG = "OneCore-App";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 🔥 CRITICAL: Hook MUST be injected during attachBaseContext
        // This ensures system structures are captured before use.
        Logger.i(TAG, "Initializing Sandbox Environment Hooks...");
        try {
            ActivityThreadHook.inject();
        } catch (Exception e) {
            Logger.e(TAG, "Early Hook Injection Failed!", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d(TAG, "Sandbox Process Created.");
    }
}
