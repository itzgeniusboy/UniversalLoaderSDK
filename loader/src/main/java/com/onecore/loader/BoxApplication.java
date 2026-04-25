package com.onecore.loader;

import android.app.Application;
import android.util.Log;

/**
 * Main Application class for OneCore Loader.
 * Simplified for minimal working version.
 */
public class BoxApplication extends Application {
    private static final String TAG = "BoxApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Application created.");
    }
}
