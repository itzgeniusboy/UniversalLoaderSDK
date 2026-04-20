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

    public PackageManagerHook(Object base, String virtualPackageName) {
        this.base = base;
        this.virtualPackageName = virtualPackageName;
    }

    public static Object createProxy(Object base, String virtualPackageName) {
        return Proxy.newProxyInstance(
            base.getClass().getClassLoader(),
            base.getClass().getInterfaces(),
            new PackageManagerHook(base, virtualPackageName)
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
                String vPath = "/data/data/com.onecore/virtual/" + virtualPackageName;
                info.dataDir = vPath;
                
                // A/B Package Support (Split APKs)
                info.sourceDir = vPath + "/base.apk";
                info.publicSourceDir = vPath + "/base.apk";
                
                // Handle Split APKs if they exist in virtual space
                if (info.splitSourceDirs != null) {
                    String[] virtualSplits = new String[info.splitSourceDirs.length];
                    for (int i = 0; i < info.splitSourceDirs.length; i++) {
                        virtualSplits[i] = vPath + "/split_" + i + ".apk";
                    }
                    info.splitSourceDirs = virtualSplits;
                    info.splitPublicSourceDirs = virtualSplits;
                }
                
                Logger.d(TAG, "ApplicationInfo virtualized for A/B package.");
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
