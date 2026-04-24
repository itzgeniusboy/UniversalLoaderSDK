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

        // Lie about Package Name
        if (methodName.equals("getPackageName") || methodName.equals("getCallingPackage")) {
            return fakePackageName;
        }

        // Lie about ApplicationInfo (Path redirection)
        if (methodName.equals("getApplicationInfo")) {
            ApplicationInfo info = (ApplicationInfo) method.invoke(realService, args);
            if (info != null && info.packageName.equals(fakePackageName)) {
                info.dataDir = virtualPath;
            }
            return info;
        }

        return method.invoke(realService, args);
    }
}
