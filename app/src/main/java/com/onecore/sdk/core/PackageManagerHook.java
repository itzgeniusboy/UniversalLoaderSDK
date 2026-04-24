package com.onecore.sdk.core;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import com.onecore.sdk.core.pm.VirtualPackageManager;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

/**
 * Advanced PackageManager Proxy.
 * Provides virtual app information to the system and target application.
 * Handles intent resolution and identification.
 */
public class PackageManagerHook implements InvocationHandler {
    private static final String TAG = "OneCore-PMHook";
    private final Object mBase;

    private PackageManagerHook(Object base) {
        this.mBase = base;
    }

    public static Object createProxy(Object realService) {
        try {
            // First try finding the standard IPackageManager interface
            Class<?> iPmClass = null;
            try {
                iPmClass = Class.forName("android.content.pm.IPackageManager");
            } catch (ClassNotFoundException e) {
                Logger.w(TAG, "IPackageManager class not found, using service interfaces");
            }

            if (iPmClass != null) {
                return Proxy.newProxyInstance(iPmClass.getClassLoader(), new Class[]{iPmClass}, new PackageManagerHook(realService));
            } else {
                return Proxy.newProxyInstance(realService.getClass().getClassLoader(), realService.getClass().getInterfaces(), new PackageManagerHook(realService));
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to create PackageManager proxy", e);
            return realService;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if ("getPackageInfo".equals(name)) {
            String pkgName = (String) args[0];
            PackageInfo info = VirtualPackageManager.get().getClonedPackage(pkgName);
            if (info != null) {
                Logger.v(TAG, "Spoofing getPackageInfo for: " + pkgName);
                return info;
            }
        }

        if ("getApplicationInfo".equals(name)) {
            String pkgName = (String) args[0];
            PackageInfo info = VirtualPackageManager.get().getClonedPackage(pkgName);
            if (info != null) {
                Logger.v(TAG, "Spoofing getApplicationInfo for: " + pkgName);
                return info.applicationInfo;
            }
        }

        if ("getActivityInfo".equals(name)) {
            android.content.ComponentName component = (android.content.ComponentName) args[0];
            if (component != null) {
                ActivityInfo ai = VirtualPackageManager.resolveActivity(component.getPackageName(), component.getClassName());
                if (ai != null) {
                    Logger.v(TAG, "Spoofing getActivityInfo for: " + component.flattenToString());
                    return ai;
                }
            }
        }

        if ("getServiceInfo".equals(name)) {
            android.content.ComponentName component = (android.content.ComponentName) args[0];
            if (component != null) {
                // Return virtual service info if available
                android.content.pm.ServiceInfo si = VirtualPackageManager.resolveService(component.getPackageName(), component.getClassName());
                if (si != null) return si;
            }
        }

        if ("getReceiverInfo".equals(name)) {
            android.content.ComponentName component = (android.content.ComponentName) args[0];
            if (component != null) {
                android.content.pm.ActivityInfo ai = VirtualPackageManager.resolveReceiver(component.getPackageName(), component.getClassName());
                if (ai != null) return ai;
            }
        }

        if ("getProviderInfo".equals(name)) {
            android.content.ComponentName component = (android.content.ComponentName) args[0];
            if (component != null) {
                android.content.pm.ProviderInfo pi = VirtualPackageManager.resolveProvider(component.getPackageName(), component.getClassName());
                if (pi != null) return pi;
            }
        }

        if ("queryIntentActivities".equals(name)) {
            Intent intent = (Intent) args[0];
            if (intent != null) {
                List<ResolveInfo> list = VirtualPackageManager.queryIntentActivities(intent);
                if (list != null && !list.isEmpty()) {
                    Logger.v(TAG, "Spoofing queryIntentActivities for: " + intent);
                    return list;
                }
            }
        }

        if ("resolveIntent".equals(name)) {
            Intent intent = (Intent) args[0];
            if (intent != null) {
                ResolveInfo ri = VirtualPackageManager.resolveIntent(intent);
                if (ri != null) {
                    Logger.v(TAG, "Spoofing resolveIntent for: " + intent);
                    return ri;
                }
            }
        }

        if ("getInstallerPackageName".equals(name)) {
            return "com.android.vending";
        }

        if ("checkPermission".equals(name)) {
            return android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            throw e.getCause();
        }
    }
}
