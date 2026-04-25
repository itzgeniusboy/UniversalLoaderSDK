package com.onecore.loader;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Host service that acts as a container for guest services.
 */
public class StubService extends Service {
    private static final String TAG = "OneCore-StubService";

    public static class P1 extends StubService {}
    public static class P2 extends StubService {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String targetService = intent.getStringExtra("target_service");
            if (targetService != null) {
                Log.i(TAG, "OneCore-DEBUG: Dispatching to virtual service -> " + targetService);
                com.onecore.sdk.core.OneCoreServiceContainer.startService(this, intent);
            }
        }
        return START_STICKY;
    }
}
