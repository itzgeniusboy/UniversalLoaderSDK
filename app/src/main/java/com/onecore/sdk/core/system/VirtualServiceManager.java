package com.onecore.sdk.core.system;

import android.content.Intent;
import android.os.IBinder;
import com.onecore.sdk.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages virtual services for apps like WhatsApp/Instagram.
 * Without this, clones will crash when trying to start background sync.
 */
public class VirtualServiceManager {
    private static final String TAG = "VirtualServiceManager";
    private static final VirtualServiceManager sInstance = new VirtualServiceManager();
    private final Map<String, IBinder> mServices = new HashMap<>();

    public static VirtualServiceManager getInstance() {
        return sInstance;
    }

    public IBinder onBindService(Intent intent) {
        String action = intent.getAction();
        Logger.d(TAG, "Virtual Service Bind: " + action);
        return mServices.get(action);
    }

    public void startService(Intent intent) {
        Logger.i(TAG, "Starting Virtual Service: " + intent.getComponent());
        // In a full implementation, this would instantiate the Service class
        // inside the sandbox process.
    }
}
