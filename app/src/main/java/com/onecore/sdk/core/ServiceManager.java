package com.onecore.sdk.core;

import android.app.Service;
import android.content.Intent;
import com.onecore.sdk.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Virtual Service lifecycle.
 * Prevents OS from seeing services that aren't declared in host manifest.
 */
public class ServiceManager {
    private static final String TAG = "OneCore-ServiceMgr";
    private static final Map<String, Service> mServices = new HashMap<>();

    public static void startService(Intent intent) {
        if (intent == null || intent.getComponent() == null) return;
        
        String className = intent.getComponent().getClassName();
        Logger.d(TAG, "Starting virtual service: " + className);
        
        if (mServices.containsKey(className)) {
            mServices.get(className).onStartCommand(intent, 0, 0);
            return;
        }

        try {
            ClassLoader cl = CloneManager.getInstance().getClassLoader();
            Service service = (Service) cl.loadClass(className).newInstance();
            // In a real implementation, we'd need to call service.attach() with a fake context
            service.onCreate();
            service.onStartCommand(intent, 0, 0);
            mServices.put(className, service);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start virtual service", e);
        }
    }

    public static void stopService(Intent intent) {
        if (intent == null || intent.getComponent() == null) return;
        String className = intent.getComponent().getClassName();
        Service service = mServices.remove(className);
        if (service != null) {
            service.onDestroy();
        }
    }
}
