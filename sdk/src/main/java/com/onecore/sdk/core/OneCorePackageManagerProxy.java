package com.onecore.sdk.core;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy for IPackageManager to spoof guest app information.
 */
public class OneCorePackageManagerProxy implements InvocationHandler {
    private static final String TAG = "OneCore-PMProxy";
    private final Object mBase;
    private static final Map<String, android.content.pm.PackageInfo> sVirtualPackages = new HashMap<>();

    public OneCorePackageManagerProxy(Object base) {
        this.mBase = base;
    }

    public static void registerPackage(android.content.pm.PackageInfo info) {
        if (info != null) sVirtualPackages.put(info.packageName, info);
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
                new OneCorePackageManagerProxy(rawPm)
            );
            
            // Hook ActivityThread.sPackageManager
            Field sPmField = activityThreadClass.getDeclaredField("sPackageManager");
            sPmField.setAccessible(true);
            sPmField.set(null, proxy);
            
            // Hook AppGlobals.getPackageManager() via its internal cache if possible
            try {
                Class<?> appGlobalsClass = Class.forName("android.app.AppGlobals");
                // AppGlobals.getPackageManager() usually just calls ActivityThread.getPackageManager()
                // So hooking sPackageManager is usually enough, but some versions cache it differently.
                Log.d(TAG, "AppGlobals checked.");
            } catch (Exception ignored) {}
            
            Log.i(TAG, "OneCore-DEBUG: IPackageManager hooked successfully.");
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: IPackageManager hook FAILED !!!", e);
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
                return sVirtualPackages.get(pkg).applicationInfo;
            }
        }
        
        return method.invoke(mBase, args);
    }
}
