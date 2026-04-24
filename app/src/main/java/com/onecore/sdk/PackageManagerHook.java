package com.onecore.sdk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Hooks IPackageManager to provide a virtualized view of installed apps.
 * Tricks cloned apps into thinking they are the only ones or that others are installed.
 */
public class PackageManagerHook implements InvocationHandler {
    private static final String TAG = "PackageManagerHook";
    private final Object base;
    private final String virtualPackageName;
    private final String virtualPath;

    public PackageManagerHook(Object base, String virtualPackageName, String virtualPath) {
        this.base = base;
        this.virtualPackageName = virtualPackageName;
        this.virtualPath = virtualPath;
    }

    public static Object createProxy(Object base, String virtualPackageName, String virtualPath) {
        return Proxy.newProxyInstance(
            base.getClass().getClassLoader(),
            base.getClass().getInterfaces(),
            new PackageManagerHook(base, virtualPackageName, virtualPath)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if ("getPackageInfo".equals(methodName)) {
            String pkgName = (String) args[0];
            Logger.d(TAG, "Intercepted getPackageInfo for: " + pkgName);
            // If it's our virtual app, we might return modified info
        } else if ("getApplicationInfo".equals(methodName)) {
            String pkgName = (String) args[0];
            Object result = method.invoke(base, args);
            if (result instanceof ApplicationInfo && pkgName.equals(virtualPackageName)) {
                ApplicationInfo info = (ApplicationInfo) result;
                // Redirect data paths
                info.dataDir = virtualPath;
                
                // A/B Package Support (Split APKs)
                // Note: sourceDir should point to the REAL APK, but dataDir is virtualized
                // In some cases we might want to redirect sourceDir to base.apk in virtual space too
                
                Logger.d(TAG, "ApplicationInfo virtualized for: " + virtualPackageName);
                return info;
            }
            return result;
        } else if ("getInstalledPackages".equals(methodName)) {
            // Filter list to only show virtual apps or a clean state
            Logger.d(TAG, "Intercepted getInstalledPackages");
        }

        return method.invoke(base, args);
    }
}
