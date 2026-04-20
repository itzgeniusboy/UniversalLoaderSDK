package com.onecore.sdk.core;

import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxies the Activity Manager to lie about Runtime Identity (UID/PID).
 */
public class ActivityManagerHook implements InvocationHandler {
    private final Object realService;

    private ActivityManagerHook(Object realService) {
        this.realService = realService;
    }

    public static Object createProxy(Object realService) {
        return Proxy.newProxyInstance(
                realService.getClass().getClassLoader(),
                realService.getClass().getInterfaces(),
                new ActivityManagerHook(realService)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Return Fake UID or PID if requested by the Game
        if (methodName.equals("getRunningAppProcesses")) {
            // Logic to filter or spoof the process list
            return method.invoke(realService, args);
        }

        return method.invoke(realService, args);
    }
}
