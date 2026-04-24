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
    private static final Map<String, android.os.IBinder> mBinders = new HashMap<>();

    public static void startService(android.content.Context context, Intent intent) {
        if (intent == null || intent.getComponent() == null) return;
        
        String className = intent.getComponent().getClassName();
        String pkgName = intent.getComponent().getPackageName();
        Logger.d(TAG, "Starting virtual service: " + className);
        
        if (mServices.containsKey(className)) {
            mServices.get(className).onStartCommand(intent, 0, 0);
            return;
        }

        try {
            ClassLoader cl = CloneManager.getInstance().getClassLoader();
            android.content.res.Resources res = CloneManager.getInstance().getResources();
            Service service = (Service) cl.loadClass(className).newInstance();
            
            // Apply ContextFixer before onCreate
            Context virtualContext = ContextManager.createVirtualContext(context, pkgName, cl, res);
            
            attachService(service, virtualContext, className, pkgName, cl, res);

            service.onCreate();
            service.onStartCommand(intent, 0, 0);
            mServices.put(className, service);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start virtual service " + className, e);
        }
    }

    public static android.os.IBinder bindService(android.content.Context context, Intent intent) {
        if (intent == null || intent.getComponent() == null) return null;
        
        String className = intent.getComponent().getClassName();
        String pkgName = intent.getComponent().getPackageName();
        
        if (!mServices.containsKey(className)) {
            startService(context, intent);
        }
        
        Service service = mServices.get(className);
        if (service != null) {
            if (mBinders.containsKey(className)) return mBinders.get(className);
            android.os.IBinder binder = service.onBind(intent);
            mBinders.put(className, binder);
            return binder;
        }
        return null;
    }

    private static void attachService(Service service, Context virtualContext, String className, String pkgName, ClassLoader cl, android.content.res.Resources res) {
        try {
            java.lang.reflect.Method attach = Service.class.getDeclaredMethod("attach",
                android.content.Context.class,
                Class.forName("android.app.ActivityThread"),
                String.class,
                android.os.IBinder.class,
                android.app.Application.class,
                Object.class);
            attach.setAccessible(true);
            
            Object at = null;
            try {
                Class<?> atClass = Class.forName("android.app.ActivityThread");
                at = atClass.getDeclaredMethod("currentActivityThread").invoke(null);
            } catch (Exception ignored) {}

            attach.invoke(service, virtualContext, at, className, null, ApplicationManager.getVirtualApp(), null);
        } catch (Exception e) {
            Logger.w(TAG, "Standard attach failed, using context binding fallback: " + e.getMessage());
            ContextManager.bindContext(service, pkgName, cl, res);
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
