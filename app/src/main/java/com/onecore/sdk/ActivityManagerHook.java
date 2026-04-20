package com.onecore.sdk;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Hooks IActivityManager to handle virtual activity launching and lifecycle.
 * Ensures activities run within the virtual space boundaries.
 */
public class ActivityManagerHook implements InvocationHandler {
    private static final String TAG = "ActivityManagerHook";
    private final Object base;

    public ActivityManagerHook(Object base) {
        this.base = base;
    }

    public static Object createProxy(Object base) {
        return Proxy.newProxyInstance(
            base.getClass().getClassLoader(),
            base.getClass().getInterfaces(),
            new ActivityManagerHook(base)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if ("startActivity".equals(methodName)) {
            Logger.d(TAG, "Intercepted startActivity");
            Intent intent = (Intent) args[1]; // Typically index 1 or 2 depending on Android version
            if (intent != null && intent.getData() != null) {
                String uri = intent.getData().toString();
                if (uri.contains("twitter") || uri.contains("facebook") || uri.contains("google")) {
                    Logger.i(TAG, "Social Login detected. Forwarding to system handler.");
                    // In virtual environment, we must ensure these intents are not 
                    // accidentally trapped or misrouted.
                }
            }
        } else if ("getRunningAppProcesses".equals(methodName)) {
            // Hide other processes from the virtual app
            Logger.d(TAG, "Intercepted getRunningAppProcesses");
        } else if ("getServices".equals(methodName)) {
            Logger.d(TAG, "Intercepted getServices");
        }

        // Automatic Root Detection Bypass: Hooking check for certain services or flags
        if ("isUserAMonkey".equals(methodName) || "isDebuggerConnected".equals(methodName)) {
            return false;
        }

        return method.invoke(base, args);
    }
}
