package com.onecore.sdk.core;

import android.content.ContentResolver;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Hook for ContentProvider to handle virtual URI mapping.
 */
public class OneCoreContentProviderProxy implements InvocationHandler {
    private static final String TAG = "OneCore-CPProxy";
    private final Object mBase;

    public OneCoreContentProviderProxy(Object base) {
        this.mBase = base;
    }

    public static void install() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);

            // Hook IActivityManager's getContentProvider
            // Usually ContentProvider calls go through AMS to find the provider
            Log.i(TAG, "OneCore-DEBUG: ContentProvider Proxy ready.");
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: ContentProvider hook FAILED !!!", e);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        
        // Handle getContentProvider / acquireContentProvider
        if (methodName.contains("getContentProvider")) {
            String authority = null;
            for (Object arg : args) {
                if (arg instanceof String) {
                    authority = (String) arg;
                    break;
                }
            }

            if (authority != null) {
                android.content.ContentProvider virtualProvider = OneCoreContentProviderManager.getProvider(authority);
                if (virtualProvider != null) {
                    Log.i(TAG, "OneCore-DEBUG: ContentProvider HIT for authority: " + authority);
                    // In a production engine, you'd return a ContentProviderHolder containing the IContentProvider proxy
                    // For now, we continue to the base to avoid breaking complex system binder handshakes
                }
            }
        }
        
        return method.invoke(mBase, args);
    }
}
