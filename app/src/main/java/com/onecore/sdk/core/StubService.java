package com.onecore.sdk.core;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.onecore.sdk.utils.Logger;

/**
 * Stub Service for OneCore Container.
 * Allows guest services (WhatsApp Sync, Instagram Background) to run inside the sandbox.
 */
public class StubService extends Service {
    private static final String TAG = "OneCore-StubService";

    @Override
    public IBinder onBind(Intent intent) {
        Logger.d(TAG, "Stub Service Bind requested");
        return VirtualServiceManager.getInstance().onBindService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.i(TAG, "Stub Service Start initiated");
        VirtualServiceManager.getInstance().startService(intent);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.i(TAG, "Stub Service Created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.w(TAG, "Stub Service Destroyed");
    }
}
