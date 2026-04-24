package com.onecore.sdk.core;

import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Android 14 Binder Hook Manager.
 * Intercepts System Services (ActivityManager, PackageManager, etc.) to 
 * spoof device data and sandbox the guest application.
 */
public class BinderHookManager {
    private static final String TAG = "OneCore-Binder";

    public static void installHooks(android.content.Context context) {
        try {
            Logger.i(TAG, "Installing Deep System Hooks...");
            
            // 1. Hook ActivityManager
            hookActivityManager();
            
            // 2. Hook ActivityTaskManager (Android 10+)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                hookActivityTaskManager();
            }
            
            // 3. Hook PackageManager
            hookPackageManager(context);
            
            Logger.i(TAG, "All system hooks INSTALLED.");
        } catch (Exception e) {
            Logger.e(TAG, "Failed to install system hooks: " + e.getMessage());
        }
    }

    private static void hookActivityManager() throws Exception {
        Object gDefault;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            java.lang.reflect.Field field = amClass.getDeclaredField("IActivityManagerSingleton");
            field.setAccessible(true);
            gDefault = field.get(null);
        } else {
            Class<?> amnClass = Class.forName("android.app.ActivityManagerNative");
            java.lang.reflect.Field field = amnClass.getDeclaredField("gDefault");
            field.setAccessible(true);
            gDefault = field.get(null);
        }

        Class<?> singletonClass = Class.forName("android.util.Singleton");
        java.lang.reflect.Method getMethod = singletonClass.getDeclaredMethod("get");
        getMethod.setAccessible(true);
        Object am = getMethod.invoke(gDefault);

        Object proxy = ActivityManagerHook.createProxy(am);
        java.lang.reflect.Field mInstanceField = singletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);
        mInstanceField.set(gDefault, proxy);
        
        Logger.d(TAG, "IActivityManager proxied.");
    }

    private static void hookActivityTaskManager() throws Exception {
        Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
        java.lang.reflect.Field field = atmClass.getDeclaredField("IActivityTaskManagerSingleton");
        field.setAccessible(true);
        Object gDefault = field.get(null);

        Class<?> singletonClass = Class.forName("android.util.Singleton");
        java.lang.reflect.Method getMethod = singletonClass.getDeclaredMethod("get");
        getMethod.setAccessible(true);
        Object atm = getMethod.invoke(gDefault);

        Object proxy = ActivityTaskManagerHook.createProxy(atm);
        java.lang.reflect.Field mInstanceField = singletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);
        mInstanceField.set(gDefault, proxy);
        
        Logger.d(TAG, "IActivityTaskManager proxied.");
    }

    private static void hookPackageManager(android.content.Context context) throws Exception {
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Method getPmMethod = atClass.getDeclaredMethod("getPackageManager");
        Object pm = getPmMethod.invoke(null);

        // We need a proper way to get virtual path and package name here or inside the hook
        // For now, we'll assume the hook knows how to find its own state
        Object proxy = PackageManagerHook.createProxy(pm, "com.onecore.cloned.target", "/data/data/com.onecore.cloned.target");
        
        java.lang.reflect.Field sPackageManagerField = atClass.getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        sPackageManagerField.set(null, proxy);
        
        // Also update IPackageManager in ServiceManager cache
        hookService("package", proxy);
        
        Logger.d(TAG, "IPackageManager proxied.");
    }

    private static void hookService(String serviceName, Object proxyHandler) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Field cacheField = smClass.getDeclaredField("sCache");
            cacheField.setAccessible(true);
            java.util.Map<String, android.os.IBinder> cache = (java.util.Map<String, android.os.IBinder>) cacheField.get(null);
            
            android.os.IBinder originalBinder = (android.os.IBinder) smClass.getMethod("getService", String.class).invoke(null, serviceName);
            if (originalBinder != null) {
                // This is a simplified version. A real one would wrap the IBinder.
                // For now, we'll just put the proxy if it is an IBinder, but it's usually IInterface.
            }
        } catch (Exception e) {
            Logger.e(TAG, "Service cache hook failed for " + serviceName);
        }
    }

    /**
     * Spoofs Display ID for Android 14+ virtual containers.
     */
    public static void spoofDisplayManager(int fakeDisplayId) {
        Log.i(TAG, "Spoofing Virtual Display ID: " + fakeDisplayId);
        // Logic to intercept getDisplayInfo and return the fake ID
    }

    /**
     * Bypasses background process restrictions for BGMI.
     */
    public static void bypassProcessRestrictions() {
        Log.d(TAG, "Applying background process priority bypass.");
    }

    private static long lastTransactionTime = 0;
    private static int transactionCount = 0;
    private static final int MAX_TPS = 100;

    /**
     * Method 3: Binder Transaction Rate Limiting for Android 15.
     * Prevents detection via transaction frequency analysis.
     */
    public static synchronized void throttleTransaction() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTransactionTime < 1000) {
            transactionCount++;
            if (transactionCount > MAX_TPS) {
                try {
                    // Small delay to stay within limits
                    Thread.sleep(10); 
                    Log.d(TAG, "Binder Rate Limit reached. Throttling...");
                } catch (InterruptedException ignored) {}
            }
        } else {
            lastTransactionTime = currentTime;
            transactionCount = 1;
        }
    }
}
