package com.onecore.sdk.core;

import android.content.Intent;
import android.os.Build;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Hook for IActivityTaskManager (Android 10+).
 * Works alongside ActivityManagerHook to handle modern Activity launching.
 */
public class ActivityTaskManagerHook implements InvocationHandler {
    private static final String TAG = "ATM-Hook";
    private final Object realService;

    private ActivityTaskManagerHook(Object realService) {
        this.realService = realService;
    }

    public static Object createProxy(Object realService) {
        if (realService == null) return null;
        
        java.util.Set<Class<?>> interfaces = new java.util.HashSet<>();
        Class<?> current = realService.getClass();
        while (current != null) {
            for (Class<?> iface : current.getInterfaces()) {
                interfaces.add(iface);
            }
            current = current.getSuperclass();
        }
        
        return Proxy.newProxyInstance(
                realService.getClass().getClassLoader(),
                interfaces.toArray(new Class[0]),
                new ActivityTaskManagerHook(realService)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Android 10+ uses startActivity in ATM
        if (methodName.equals("startActivity")) {
            int intentIdx = findIntentIndex(args);
            if (intentIdx != -1) {
                Intent intent = (Intent) args[intentIdx];
                if (shouldRedirect(intent)) {
                    Logger.i(TAG, "Intercepting modern startActivity");
                    args[intentIdx] = wrapIntent(intent);
                }
            }
        }

        return method.invoke(realService, args);
    }

    private int findIntentIndex(Object[] args) {
        if (args == null) return -1;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Intent) return i;
        }
        return -1;
    }

    private boolean shouldRedirect(Intent intent) {
        if (intent == null || intent.getComponent() == null) return false;
        if (intent.hasExtra("_VA_NON_HOOK_")) return false;
        String pkg = intent.getComponent().getPackageName();
        return CloneManager.getInstance().getClonedPackage(pkg) != null;
    }

    private Intent wrapIntent(Intent intent) {
        String pkgName = intent.getComponent().getPackageName();
        String className = intent.getComponent().getClassName();
        
        Intent stubIntent = new Intent();
        stubIntent.setClassName(com.onecore.sdk.OneCoreSDK.getContext().getPackageName(), "com.onecore.sdk.core.StubActivity");
        stubIntent.addFlags(intent.getFlags());
        stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        stubIntent.putExtra("target_package", pkgName);
        stubIntent.putExtra("target_activity", className);
        stubIntent.putExtra("_VA_TARGET_", new Intent(intent));
        
        return stubIntent;
    }
}
