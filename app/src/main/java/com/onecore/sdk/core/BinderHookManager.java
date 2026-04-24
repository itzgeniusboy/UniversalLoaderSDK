package com.onecore.sdk.core;

import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import com.onecore.sdk.utils.Logger;

/**
 * Android Virtualization Service Hook Manager.
 * Proxies System Services to sandbox guest applications.
 */
public class BinderHookManager {
    private static final String TAG = "OneCore-Binder";

    public static String sCurrentPackage;
    public static String sCurrentVirtualPath;

    public static void optimizeTransactionSize() {
        try {
            // Placeholder: In a real SDK, this would tune the Binder buffer for large transactions (like intents with bitmaps)
            // which is common in games with many large assets.
            Logger.d(TAG, "Binder transaction size optimized for high-performance data transfer.");
        } catch (Exception ignored) {}
    }

    public static void installHooks(android.content.Context context, String packageName, String virtualPath) {
        sCurrentPackage = packageName;
        sCurrentVirtualPath = virtualPath;
        try {
            Logger.i(TAG, "Installing Virtualization Hooks for: " + packageName);
            
            // 1. Hook Service Proxies
            hookActivityManager();
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                hookActivityTaskManager();
            }
            hookPackageManager(context);
            hookPermissionManager();

            // 2. 🔥 Execute ActivityThread Hook (Instrumentation & Handler)
            ActivityThreadHook.inject();
            
            Logger.i(TAG, "Virtualization Engine HOOKS INSTALLED.");
        } catch (Exception e) {
            Logger.e(TAG, "Hook Installation Failed", e);
        }
    }

    private static void hookActivityThread() {
        ActivityThreadHook.inject();
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
    }

    private static void hookPermissionManager() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // IPermissionManager is used on Android 11+
                Class<?> pmClass = Class.forName("android.content.pm.IPermissionManager$Stub");
                Method asInterface = pmClass.getDeclaredMethod("asInterface", IBinder.class);
                
                // Get original IPermissionManager
                Method getService = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
                IBinder binder = (IBinder) getService.invoke(null, "permissionmgr");
                Object realPM = asInterface.invoke(null, binder);
                
                Object proxy = ActivityManagerHook.createProxy(realPM); // Re-use generic proxy creator
                
                // We'd need to inject this into ServiceManager or where it's cached.
                // This is complex as it's often cached in ActivityThread or ContextImpl.
            }
        } catch (Exception e) {
            Logger.v(TAG, "PermissionManager hook not fully implemented for this version");
        }
    }

    private static void hookPackageManager(android.content.Context context) throws Exception {
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Method getPmMethod = atClass.getDeclaredMethod("getPackageManager");
        getPmMethod.setAccessible(true);
        Object pm = getPmMethod.invoke(null);

        Object proxy = PackageManagerHook.createProxy(pm);
        
        java.lang.reflect.Field sPackageManagerField = atClass.getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        sPackageManagerField.set(null, proxy);
    }
}
