package com.onecore.sdk.core;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages virtual service instances.
 */
public class OneCoreServiceContainer {
    private static final String TAG = "OneCore-ServiceContainer";
    private static final Map<String, Service> mServiceMap = new HashMap<>();

    public static void startService(Context context, Intent intent) {
        ComponentName component = intent.getComponent();
        if (component == null) return;
        
        String className = component.getClassName();
        if (mServiceMap.containsKey(className)) {
            mServiceMap.get(className).onStartCommand(intent, 0, 0);
            return;
        }

        try {
            ClassLoader cl = VirtualContainer.getInstance().getClassLoader();
            Class<?> serviceClass = cl.loadClass(className);
            Service service = (Service) serviceClass.newInstance();
            
            // Fix context and attach
            OneCoreContextFixer.fixContext(service, component.getPackageName());
            
            // Reflection to call attach
            java.lang.reflect.Method attach = Service.class.getDeclaredMethod("attach",
                    Context.class, 
                    Class.forName("android.app.ActivityThread"),
                    String.class,
                    android.os.IBinder.class,
                    android.app.Application.class,
                    Object.class);
            
            attach.setAccessible(true);
            
            // Get internal dependencies from ActivityThread
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object at = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null);
            
            attach.invoke(service, 
                    service, 
                    at, 
                    className, 
                    new android.os.Binder(), 
                    VirtualContainer.getInstance().getTargetApplication(),
                    at.getClass().getDeclaredMethod("getHandler").invoke(at));

            service.onCreate();
            service.onStartCommand(intent, 0, 0);
            
            mServiceMap.put(className, service);
            Log.i(TAG, "OneCore-DEBUG: Virtual Service started -> " + className);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start virtual service: " + className, e);
        }
    }

    public static void stopService(String className) {
        Service service = mServiceMap.remove(className);
        if (service != null) {
            service.onDestroy();
            Log.i(TAG, "OneCore-DEBUG: Virtual Service stopped -> " + className);
        }
    }
}
