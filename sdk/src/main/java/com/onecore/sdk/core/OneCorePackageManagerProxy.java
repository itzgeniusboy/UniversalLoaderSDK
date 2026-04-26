package com.onecore.sdk.core;

import android.util.Log;
import com.onecore.sdk.utils.ReflectionHelper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Version-adaptive Proxy for IPackageManager to spoof guest app information.
 */
public class OneCorePackageManagerProxy implements InvocationHandler {
    private static final String TAG = "OneCore-PMProxy";
    private final Object mBase;
    private static final Map<String, android.content.pm.PackageInfo> sVirtualPackages = new HashMap<>();
    private static final java.util.Set<String> sHiddenPackages = new java.util.HashSet<>();

    public OneCorePackageManagerProxy(Object base) {
        this.mBase = base;
    }

    public static void hidePackage(String packageName) {
        sHiddenPackages.add(packageName);
    }

    public static void registerPackage(android.content.pm.PackageInfo info) {
        if (info != null) {
            OneCoreSignatureProxy.spoofSignature(info);
            sVirtualPackages.put(info.packageName, info);
        }
    }

    public static android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName component) {
        if (component == null) return null;
        android.content.pm.PackageInfo info = sVirtualPackages.get(component.getPackageName());
        if (info != null && info.activities != null) {
            for (android.content.pm.ActivityInfo ai : info.activities) {
                if (ai.name.equals(component.getClassName())) {
                    return ai;
                }
            }
        }
        return null;
    }

    public static void install() {
        SafeExecutionManager.run("PMS Hook", () -> {
            // Mock GMS and Play Store for games
            android.content.pm.PackageInfo gms = new android.content.pm.PackageInfo();
            gms.packageName = "com.google.android.gms";
            gms.versionCode = 233013000;
            gms.applicationInfo = new android.content.pm.ApplicationInfo();
            gms.applicationInfo.packageName = gms.packageName;
            gms.applicationInfo.flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM;
            sVirtualPackages.put(gms.packageName, gms);

            android.content.pm.PackageInfo vending = new android.content.pm.PackageInfo();
            vending.packageName = "com.android.vending";
            vending.versionCode = 83611310;
            vending.applicationInfo = new android.content.pm.ApplicationInfo();
            vending.applicationInfo.packageName = vending.packageName;
            vending.applicationInfo.flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM;
            sVirtualPackages.put(vending.packageName, vending);

            Object rawPm = ReflectionHelper.invokeMethod(null, "getPackageManager");
            if (rawPm == null) {
                // Fallback attempt via ActivityThread
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                rawPm = ReflectionHelper.invokeMethod(null, "getPackageManager");
            }

            if (rawPm != null && rawPm.getClass().getName().contains("com.onecore.sdk.core")) {
                Log.w(TAG, "PMS already hooked, skipping.");
                return;
            }

            Class<?> iPmClass = Class.forName("android.content.pm.IPackageManager");
            Object proxy = Proxy.newProxyInstance(
                iPmClass.getClassLoader(),
                new Class[]{iPmClass},
                new OneCorePackageManagerProxy(rawPm)
            );
            
            // Hook multiple possible locations for PMS cache
            ReflectionHelper.setFieldValue(null, proxy, "sPackageManager"); // ActivityThread
            
            Log.i(TAG, "OneCore-DEBUG: IPackageManager hooked successfully.");
        });
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        
        if ("getPackageInfo".equals(methodName)) {
            String pkg = (String) args[0];
            if (sHiddenPackages.contains(pkg)) return null;
            if (sVirtualPackages.containsKey(pkg)) {
                return sVirtualPackages.get(pkg);
            }
        } else if ("getActivityInfo".equals(methodName)) {
            android.content.ComponentName component = (android.content.ComponentName) args[0];
            android.content.pm.ActivityInfo ai = getActivityInfo(component);
            if (ai != null) return ai;
        } else if ("getInstallerPackageName".equals(methodName)) {
            return "com.android.vending"; // Spoof Play Store as installer
        } else if ("getApplicationInfo".equals(methodName)) {
            String pkg = (String) args[0];
            if (sHiddenPackages.contains(pkg)) return null;
            if (sVirtualPackages.containsKey(pkg)) {
                return sVirtualPackages.get(pkg).applicationInfo;
            }
        } else if ("checkPermission".equals(methodName)) {
            String pkg = (String) args[args.length - 1] instanceof String ? (String) args[args.length - 1] : null;
            if (pkg != null && sVirtualPackages.containsKey(pkg)) {
                return android.content.pm.PackageManager.PERMISSION_GRANTED;
            }
        }
        
        return method.invoke(mBase, args);
    }
}
