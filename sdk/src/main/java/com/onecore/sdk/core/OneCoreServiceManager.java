package com.onecore.sdk.core;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages hooks for various system services.
 */
public class OneCoreServiceManager {
    private static final String TAG = "OneCore-ServiceManager";
    private static final Map<String, Object> sProxiedServices = new HashMap<>();

    public static void install(Context context) {
        Log.i(TAG, "OneCore-DEBUG: Installing System Service Proxies...");
        
        // 1. Hook Activity Manager (already started in AMSProxy, let's unify)
        OneCoreAMSProxy.install(context.getPackageName());
        
        // 2. Hook Package Manager
        OneCorePackageManagerProxy.install();
        
        // 3. Hook Other Services via ServiceManager.getService
        OneCoreDisplayProxy.install();
        
        hookServiceInServiceManager("notification");
        hookServiceInServiceManager("connectivity");
        hookServiceInServiceManager("location");
    }

    private static void hookServiceInServiceManager(String serviceName) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = smClass.getDeclaredMethod("getService", String.class);
            Object rawService = getServiceMethod.invoke(null, serviceName);
            
            if (rawService == null) return;

            // This is complex because getService returns IBinder, 
            // but we need to hook the actual interface.
            // For now, most crucial are AMS and PMS which we hook via ActivityThread.
            Log.d(TAG, "Hooking service: " + serviceName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to hook service: " + serviceName, e);
        }
    }
}
