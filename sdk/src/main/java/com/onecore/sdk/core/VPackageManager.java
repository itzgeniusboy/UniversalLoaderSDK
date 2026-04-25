package com.onecore.sdk.core;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxy for IPackageManager to intercept system calls.
 */
public class VPackageManager implements InvocationHandler {
    private static final String TAG = "VPackageManager";
    private final Object mBase;
    private static java.util.Map<String, android.content.pm.PackageInfo> sVirtualPackages = new java.util.HashMap<>();

    public VPackageManager(Object base) {
        this.mBase = base;
    }

    public static void registerPackage(android.content.pm.PackageInfo info) {
        if (info != null) {
            sVirtualPackages.put(info.packageName, info);
            Log.d(TAG, "Registered virtual package: " + info.packageName);
        }
    }

    public static void install() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method sPmMethod = activityThreadClass.getDeclaredMethod("getPackageManager");
            sPmMethod.setAccessible(true);
            Object rawPm = sPmMethod.invoke(null);
            
            Class<?> iPmClass = Class.forName("android.content.pm.IPackageManager");
            Object proxy = Proxy.newProxyInstance(
                iPmClass.getClassLoader(),
                new Class[]{iPmClass},
                new VPackageManager(rawPm)
            );
            
            // Replace in ActivityThread
            Field sPmField = activityThreadClass.getDeclaredField("sPackageManager");
            sPmField.setAccessible(true);
            sPmField.set(null, proxy);
            
            Log.i(TAG, "IPackageManager successfully hooked.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to hook IPackageManager", e);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        
        if ("getPackageInfo".equals(methodName)) {
            String pkg = (String) args[0];
            if (sVirtualPackages.containsKey(pkg)) {
                Log.d(TAG, "OneCore-DEBUG: IPackageManager.getPackageInfo -> " + pkg);
                return sVirtualPackages.get(pkg);
            }
        } else if ("getApplicationInfo".equals(methodName)) {
            String pkg = (String) args[0];
            if (sVirtualPackages.containsKey(pkg)) {
                Log.d(TAG, "OneCore-DEBUG: IPackageManager.getApplicationInfo -> " + pkg);
                return sVirtualPackages.get(pkg).applicationInfo;
            }
        } else if ("getActivityInfo".equals(methodName)) {
             android.content.ComponentName component = (android.content.ComponentName) args[0];
             if (component != null && sVirtualPackages.containsKey(component.getPackageName())) {
                 android.content.pm.PackageInfo info = sVirtualPackages.get(component.getPackageName());
                 if (info.activities != null) {
                     for (android.content.pm.ActivityInfo ai : info.activities) {
                         if (ai.name.equals(component.getClassName())) {
                             Log.d(TAG, "Spoofing getActivityInfo for " + component);
                             return ai;
                         }
                     }
                 }
             }
        }
        
        return method.invoke(mBase, args);
    }
}
