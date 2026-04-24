package com.onecore.sdk.core;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import com.onecore.sdk.core.pm.VirtualPackageManager;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Advanced PackageManager Proxy.
 * Provides virtual app information to the system and target application.
 */
public class PackageManagerHook implements InvocationHandler {
    private static final String TAG = "OneCore-PMHook";
    private final Object mBase;

    private PackageManagerHook(Object base) {
        this.mBase = base;
    }

    public static Object createProxy(Object realService) {
        try {
            Class<?> iPmClass = Class.forName("android.content.pm.IPackageManager");
            return Proxy.newProxyInstance(iPmClass.getClassLoader(), new Class[]{iPmClass}, new PackageManagerHook(realService));
        } catch (Exception e) {
            return Proxy.newProxyInstance(realService.getClass().getClassLoader(), realService.getClass().getInterfaces(), new PackageManagerHook(realService));
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if ("getPackageInfo".equals(name)) {
            String pkgName = (String) args[0];
            PackageInfo info = VirtualPackageManager.get().getClonedPackage(pkgName);
            if (info != null) return info;
        }

        if ("getApplicationInfo".equals(name)) {
            String pkgName = (String) args[0];
            PackageInfo info = VirtualPackageManager.get().getClonedPackage(pkgName);
            if (info != null) return info.applicationInfo;
        }

        if ("getActivityInfo".equals(name)) {
            android.content.ComponentName component = (android.content.ComponentName) args[0];
            if (component != null) {
                ActivityInfo ai = VirtualPackageManager.resolveActivity(component.getPackageName(), component.getClassName());
                if (ai != null) return ai;
            }
        }

        if ("getServiceInfo".equals(name)) {
            android.content.ComponentName component = (android.content.ComponentName) args[0];
            if (component != null) {
                // Return virtual service info if available
            }
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
