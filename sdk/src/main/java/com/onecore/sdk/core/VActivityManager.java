package com.onecore.sdk.core;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxy for IActivityManager to intercept system calls.
 */
public class VActivityManager implements InvocationHandler {
    private static final String TAG = "VActivityManager";
    private final Object mBase;
    private static String sHostPackage;

    public VActivityManager(Object base) {
        this.mBase = base;
    }

    public static void install(String hostPackage) {
        sHostPackage = hostPackage;
        try {
            Object gDefault;
            Field gDefaultField;
            
            // Android 8.0+ uses ActivityManager.getService() -> IActivityManagerSingleton
            try {
                Class<?> amClass = Class.forName("android.app.ActivityManager");
                gDefaultField = amClass.getDeclaredField("IActivityManagerSingleton");
            } catch (Exception e) {
                // Older versions
                Class<?> amNativeClass = Class.forName("android.app.ActivityManagerNative");
                gDefaultField = amNativeClass.getDeclaredField("gDefault");
            }
            
            gDefaultField.setAccessible(true);
            gDefault = gDefaultField.get(null);
            
            Class<?> singletonClass = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonClass.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);
            
            Object rawBinder = mInstanceField.get(gDefault);
            if (rawBinder == null) {
                // Force initialization by calling getService
                Method getService = gDefault.getClass().getMethod("get");
                rawBinder = getService.invoke(gDefault);
            }
            
            Class<?> iAmClass = Class.forName("android.app.IActivityManager");
            Object proxy = Proxy.newProxyInstance(
                iAmClass.getClassLoader(),
                new Class[]{iAmClass},
                new VActivityManager(rawBinder)
            );
            
            mInstanceField.set(gDefault, proxy);
            Log.i(TAG, "IActivityManager successfully hooked.");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to hook IActivityManager", e);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        
        if (methodName.contains("startActivity")) {
            int intentIndex = -1;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof android.content.Intent) {
                    intentIndex = i;
                    break;
                }
            }
            
            if (intentIndex != -1) {
                android.content.Intent intent = (android.content.Intent) args[intentIndex];
                android.content.Intent rewritten = StubActivityManager.replaceWithStub(intent, sHostPackage);
                if (rewritten != intent) {
                    Log.d(TAG, "OneCore-DEBUG: IActivityManager.startActivity rewritten.");
                    args[intentIndex] = rewritten;
                }
            }
        }
        
        return method.invoke(mBase, args);
    }
}
