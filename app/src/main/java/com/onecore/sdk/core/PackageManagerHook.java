package com.onecore.sdk.core;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import com.onecore.sdk.core.pm.VirtualPackageManager;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Advanced PackageManager Proxy.
 * Spoofs system responses to pretend virtual apps are installed natively.
 */
public class PackageManagerHook implements InvocationHandler {
    private static final String TAG = "OneCore-PMHook";
    private final Object mBase;

    private PackageManagerHook(Object base) {
        this.mBase = base;
    }

    public static Object createProxy(Object realService) {
        Class<?> clazz = realService.getClass();
        return Proxy.newProxyInstance(clazz.getClassLoader(), clazz.getInterfaces(), new PackageManagerHook(realService));
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
            // Logic to return virtual activity info
        }

        if ("getPackageInstaller".equals(name)) {
            // Often used for verification, might need spoofing
        }

        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            throw e.getCause();
        }
    }
}
