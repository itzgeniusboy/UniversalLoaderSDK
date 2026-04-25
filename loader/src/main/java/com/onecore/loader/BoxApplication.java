package com.onecore.loader;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * Main Application class for OneCore Loader.
 * Simplified for minimal working version.
 */
public class BoxApplication extends Application {
    private static final String TAG = "BoxApplication";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // Deep initial hook
        com.onecore.sdk.core.OneCoreActivityThreadHook.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Application onCreate.");
        
        // Ensure SDK is active
        com.onecore.sdk.OneCoreSDK.init(this);
    }
}
