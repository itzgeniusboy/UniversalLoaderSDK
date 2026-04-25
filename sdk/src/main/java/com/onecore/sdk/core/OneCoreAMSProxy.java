package com.onecore.sdk.core;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxy for IActivityManager / IActivityTaskManager to intercept startActivity.
 */
public class OneCoreAMSProxy implements InvocationHandler {
    private static final String TAG = "OneCore-AMSProxy";
    private final Object mBase;
    private static String sHostPackage;

    public OneCoreAMSProxy(Object base) {
        this.mBase = base;
    }

    public static void install(String hostPackage) {
        sHostPackage = hostPackage;
        try {
            Object gDefault;
            Field gDefaultField;
            
            // Try ActivityTaskManager (Android 10+)
            try {
                Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
                gDefaultField = atmClass.getDeclaredField("IActivityTaskManagerSingleton");
            } catch (Exception e) {
                // Fallback to ActivityManager
                Class<?> amClass = Class.forName("android.app.ActivityManager");
                gDefaultField = amClass.getDeclaredField("IActivityManagerSingleton");
            }
            
            gDefaultField.setAccessible(true);
            gDefault = gDefaultField.get(null);
            
            Class<?> singletonClass = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonClass.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);
            
            Object rawInstance = mInstanceField.get(gDefault);
            if (rawInstance == null) {
                Method getMethod = singletonClass.getDeclaredMethod("get");
                rawInstance = getMethod.invoke(gDefault);
            }
            
            Class<?> iAmClass = rawInstance.getClass().getInterfaces()[0]; // Simplification
            // Better: find IActivityManager or IActivityTaskManager explicitly
            for (Class<?> iface : rawInstance.getClass().getInterfaces()) {
                if (iface.getName().contains("IActivityManager") || iface.getName().contains("IActivityTaskManager")) {
                    iAmClass = iface;
                    break;
                }
            }

            Object proxy = Proxy.newProxyInstance(
                iAmClass.getClassLoader(),
                new Class[]{iAmClass},
                new OneCoreAMSProxy(rawInstance)
            );
            
            mInstanceField.set(gDefault, proxy);
            Log.i(TAG, "OneCore-DEBUG: AMS hooked successfully.");
            
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: AMS hook FAILED !!!", e);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().contains("startActivity")) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof android.content.Intent) {
                    android.content.Intent intent = (android.content.Intent) args[i];
                    android.content.Intent rewritten = OneCoreStubManager.replaceWithStub(intent, sHostPackage);
                    if (rewritten != intent) {
                        Log.d(TAG, "OneCore-DEBUG: IActivityManager.startActivity rewritten.");
                        args[i] = rewritten;
                    }
                    break;
                }
            }
        }
        return method.invoke(mBase, args);
    }
}
