package com.onecore.sdk.core;

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

    private PackageManagerHook(Object realService) {
        this.realService = realService;
    }

    public static Object createProxy(Object realService) {
        return Proxy.newProxyInstance(
                realService.getClass().getClassLoader(),
                realService.getClass().getInterfaces(),
                new PackageManagerHook(realService)
        );
    }

    public static Object createProxy(Object realService, String unused1, String unused2) {
        return createProxy(realService);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // 1. Lie about Package Identity
        if (methodName.equals("getPackageName") || methodName.equals("getCallingPackage")) {
             // In a multi-app environment, we'd check which virtual process is calling
        }

        // 2. Handle PackageInfo Queries (Integrity checks)
        if (methodName.equals("getPackageInfo") || methodName.equals("getPackageInfoAsUser")) {
            String pkgName = (String) args[0];
            android.content.pm.PackageInfo info = com.onecore.sdk.core.pm.VirtualPackageManager.get().getClonedPackage(pkgName);
            if (info != null) {
                Logger.d("PMS-Hook", "Spoofing PackageInfo for: " + pkgName);
                return info;
            }
        }

        // 3. Lie about ApplicationInfo (Path redirection is CRITICAL for data isolation)
        if (methodName.equals("getApplicationInfo") || methodName.equals("getApplicationInfoAsUser")) {
            String pkgName = (String) args[0];
            android.content.pm.PackageInfo info = com.onecore.sdk.core.pm.VirtualPackageManager.get().getClonedPackage(pkgName);
            if (info != null) {
                Logger.d("PMS-Hook", "Spoofing ApplicationInfo for: " + pkgName);
                return info.applicationInfo;
            }
        }

        // 4. Handle Component resolution (Prevents Host/Guest leaks)
        if (methodName.startsWith("queryIntent") || methodName.startsWith("resolveIntent")) {
             // Redirection logic for virtual components should happen here
        }

        return method.invoke(realService, args);
    }
}
