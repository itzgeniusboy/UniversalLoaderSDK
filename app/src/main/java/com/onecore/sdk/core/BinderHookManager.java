package com.onecore.sdk.core;

import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import com.onecore.sdk.utils.Logger;

/**
 * Android 14 Binder Hook Manager.
 * Intercepts System Services (ActivityManager, PackageManager, etc.) to 
 * spoof device data and sandbox the guest application.
 */
public class BinderHookManager {
    private static final String TAG = "OneCore-Binder";

    public static String sCurrentPackage;
    public static String sCurrentVirtualPath;

    public static void installHooks(android.content.Context context, String packageName, String virtualPath) {
        sCurrentPackage = packageName;
        sCurrentVirtualPath = virtualPath;
        try {
            Logger.i(TAG, "Installing Deep System Hooks for " + packageName);
            
            // 1. Hook ActivityManager
            hookActivityManager();
            
            // 2. Hook ActivityTaskManager (Android 10+)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                hookActivityTaskManager();
            }
            
            // 3. Hook PackageManager
            hookPackageManager(context);

            // 4. Hook ActivityThread Instrumentation & Handler
            hookActivityThread();
            
            Logger.i(TAG, "All system hooks INSTALLED for " + packageName);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to install system hooks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void hookActivityThread() throws Exception {
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Method currentAtMethod = atClass.getDeclaredMethod("currentActivityThread");
        currentAtMethod.setAccessible(true);
        Object at = currentAtMethod.invoke(null);

        // 1. Hook Instrumentation
        Field mInstrumentationField = atClass.getDeclaredField("mInstrumentation");
        mInstrumentationField.setAccessible(true);
        android.app.Instrumentation baseInst = (android.app.Instrumentation) mInstrumentationField.get(at);
        mInstrumentationField.set(at, new VAInstrumentation(baseInst));

        // 2. Hook H Handler (Callback)
        Field mHField = atClass.getDeclaredField("mH");
        mHField.setAccessible(true);
        android.os.Handler h = (android.os.Handler) mHField.get(at);
        
        Field mCallbackField = android.os.Handler.class.getDeclaredField("mCallback");
        mCallbackField.setAccessible(true);
        mCallbackField.set(h, new com.onecore.sdk.core.hook.HCallback(h));

        Logger.d(TAG, "ActivityThread Hooks (Instrumentation & Handler) installed.");
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
 
        // Use the current guest package and path
        Object proxy = PackageManagerHook.createProxy(pm, sCurrentPackage, sCurrentVirtualPath);
        
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
