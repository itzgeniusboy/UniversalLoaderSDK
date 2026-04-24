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

        if ("getAppResourcePackageName".equals(name) || "getAppResourcePackageNameAsUser".equals(name)) {
            return BinderHookManager.sCurrentPackage;
        }

        if (args != null && args.length > 0) {
            if ("getPackageInfo".equals(name) || "getPackageInfoAsUser".equals(name)) {
                String pkgName = (String) args[0];
                int flags = 0;
                if (args.length > 1 && args[1] instanceof Integer) {
                    flags = (int) args[1];
                }
                
                PackageInfo info = VirtualPackageManager.get().getClonedPackage(pkgName);
                if (info != null) {
                    Logger.v(TAG, "Spoofing " + name + " for: " + pkgName + " (flags: " + flags + ")");
                    // In a real implementation we would filter fields based on flags
                    return info;
                }
            }

            if ("getApplicationInfo".equals(name) || "getApplicationInfoAsUser".equals(name)) {
                String pkgName = (String) args[0];
                PackageInfo info = VirtualPackageManager.get().getClonedPackage(pkgName);
                if (info != null) {
                    Logger.v(TAG, "Spoofing " + name + " for: " + pkgName);
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
        }

        if ("resolveContentProvider".equals(name)) {
            String authority = (String) args[0];
            android.content.pm.ProviderInfo pi = VirtualPackageManager.resolveProviderByAuthority(authority);
            if (pi != null) {
                Logger.v(TAG, "Spoofing resolveContentProvider for: " + authority);
                return pi;
            }
        }

        if ("getInstallerPackageName".equals(name)) {
            return "com.android.vending";
        }

        if ("getPackagesForUid".equals(name)) {
            int uid = (int) args[0];
            if (uid == android.os.Process.myUid()) {
                return new String[]{BinderHookManager.sCurrentPackage};
            }
        }

        if ("getNameForUid".equals(name)) {
            int uid = (int) args[0];
            if (uid == android.os.Process.myUid()) {
                return BinderHookManager.sCurrentPackage;
            }
        }

        if ("checkPermission".equals(name)) {
            String permName = (String) args[0];
            String pkgName = (String) args[1];
            return PermissionManager.checkPermission(permName, pkgName, android.os.Process.myUid());
        }

        if ("checkUidPermission".equals(name)) {
            return android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        if ("isSafeMode".equals(name)) {
            return false;
        }

        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            throw e.getCause();
        }
    }
}
