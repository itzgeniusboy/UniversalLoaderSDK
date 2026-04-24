package com.onecore.sdk.core;

import android.content.pm.ApplicationInfo;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxies the System Package Manager to "lie" to the game.
 * When the game asks "What is my package name?", we return the Fake Name.
 */
public class PackageManagerHook implements InvocationHandler {
    private final Object realService;
    private final String fakePackageName;
    private final String virtualPath;

    private PackageManagerHook(Object realService, String fakePackageName, String virtualPath) {
        this.realService = realService;
        this.fakePackageName = fakePackageName;
        this.virtualPath = virtualPath;
    }

    public static Object createProxy(Object realService, String fakePackageName, String virtualPath) {
        return Proxy.newProxyInstance(
                realService.getClass().getClassLoader(),
                realService.getClass().getInterfaces(),
                new PackageManagerHook(realService, fakePackageName, virtualPath)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // 1. Lie about Package Identity
        if (methodName.equals("getPackageName") || methodName.equals("getCallingPackage")) {
            return fakePackageName;
        }

        // 2. Handle PackageInfo Queries (Integrity checks)
        if (methodName.equals("getPackageInfo") || methodName.equals("getPackageInfoAsUser")) {
            String pkgName = (String) args[0];
            if (pkgName.equals(fakePackageName)) {
                Logger.d("PMS-Hook", "Spoofing PackageInfo for: " + pkgName);
                return com.onecore.sdk.core.pm.VirtualPackageManager.get().getClonedPackage(pkgName);
            }
        }

        // 3. Lie about ApplicationInfo (Path redirection is CRITICAL for data isolation)
        if (methodName.equals("getApplicationInfo") || methodName.equals("getApplicationInfoAsUser")) {
            String pkgName = (String) args[0];
            if (pkgName.equals(fakePackageName)) {
                android.content.pm.PackageInfo info = com.onecore.sdk.core.pm.VirtualPackageManager.get().getClonedPackage(fakePackageName);
                if (info != null) {
                    Logger.d("PMS-Hook", "Spoofing ApplicationInfo for: " + pkgName);
                    return info.applicationInfo;
                }
            }
        }

        // 4. Handle Component resolution (Prevents Host/Guest leaks)
        if (methodName.startsWith("queryIntent") || methodName.startsWith("resolveIntent")) {
             // In production, we iterate through results and filter/replace with guest components
        }

        return method.invoke(realService, args);
    }
}
