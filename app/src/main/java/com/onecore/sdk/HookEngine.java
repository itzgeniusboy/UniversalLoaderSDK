package com.onecore.sdk;

import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced Hook Engine for OneCore SDK Engine.
 * Supports interface-based proxying and a "Method Interceptor" pattern 
 * for arbitrary objects via reflection-based wrappers.
 */
public class HookEngine {
    private static final String TAG = "HookEngine";
    private static HookEngine instance;
    private final Map<String, HookCallback> hooks = new HashMap<>();

    public interface HookCallback {
        Object onInvoke(Object target, Method method, Object[] args) throws Throwable;
    }

    private NativeHook nativeHook;

    private HookEngine() {
        if (NativeHook.isAvailable()) {
            nativeHook = new NativeHook();
        }
    }

    public static synchronized HookEngine getInstance() {
        if (instance == null) {
            instance = new HookEngine();
        }
        return instance;
    }

    public void init() {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Logger.d(TAG, "Enhanced Hook Engine initialized.");
    }

    /**
     * Native Hooking wrapper
     */
    public long hookNativeFunction(long targetAddr, long replaceAddr) {
        if (!SDKLicense.getInstance().isLicensed()) return -1;
        if (nativeHook != null) {
            return nativeHook.hookFunction(targetAddr, replaceAddr);
        }
        Logger.e(TAG, "Native hooking not available.");
        return -1;
    }

    /**
     * Hooks an interface-based object.
     */
    @SuppressWarnings("unchecked")
    public <T> T hookInterface(final T target, Class<T> interfaceClass, final HookCallback callback) {
        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[]{interfaceClass},
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (callback != null) {
                        return callback.onInvoke(target, method, args);
                    }
                    return method.invoke(target, args);
                }
            }
        );
    }

    /**
     * Reflection-based Method Interceptor.
     * Wraps any object and intercepts calls made via this interceptor.
     */
    public Object callMethod(Object target, String methodName, Object[] args, HookCallback callback) throws Throwable {
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }
        
        Method method = target.getClass().getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);

        if (callback != null) {
            return callback.onInvoke(target, method, args);
        }
        
        return method.invoke(target, args);
    }

    public void registerHook(String key, HookCallback callback) {
        hooks.put(key, callback);
    }
}
