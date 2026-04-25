package com.onecore.loader;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Service to handle the virtualization environment.
 * Simplified for minimal working version.
 */
public class LoaderService extends Service {
    private static final String TAG = "LoaderService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed.");
    }
}
