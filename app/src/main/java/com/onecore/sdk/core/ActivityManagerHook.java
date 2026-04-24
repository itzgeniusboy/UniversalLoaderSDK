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

        if (methodName.equals("startActivity")) {
            int intentIdx = -1;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof android.content.Intent) {
                    intentIdx = i;
                    break;
                }
            }

            if (intentIdx != -1) {
                android.content.Intent intent = (android.content.Intent) args[intentIdx];
                if (intent.getComponent() != null) {
                    String pkgName = intent.getComponent().getPackageName();
                    String className = intent.getComponent().getClassName();

                    // If it's a guest activity launch, wrap it
                    if (com.onecore.sdk.VirtualContainer.getInstance().getClonedPackage(pkgName) != null) {
                        Logger.i("ActivityManagerHook", "Redirecting Intent to StubActivity: " + className);
                        android.content.Intent stubIntent = new android.content.Intent();
                        stubIntent.setClassName(com.onecore.sdk.OneCoreSDK.getContext().getPackageName(), "com.onecore.sdk.core.StubActivity");
                        stubIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        stubIntent.putExtra("target_package", pkgName);
                        stubIntent.putExtra("target_activity", className);
                        
                        args[intentIdx] = stubIntent;
                    }
                }
            }
        }

        // Return Fake UID or PID if requested by the Game
        if (methodName.equals("getRunningAppProcesses")) {
            // Logic to filter or spoof the process list
            return method.invoke(realService, args);
        }

        return method.invoke(realService, args);
    }
}
