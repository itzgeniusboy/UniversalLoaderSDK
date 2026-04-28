package com.onecore.sdk.core.hook;

import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Base class for all system service proxies.
 * Matches the logic in 'black.android.app.IActivityManager' in your reference.
 */
public abstract class ServiceProxy implements InvocationHandler {
    protected final String TAG = getClass().getSimpleName();
    protected Object mOriginal;

    public ServiceProxy(Object original) {
        this.mOriginal = original;
    }

    public Object getProxy() {
        return Proxy.newProxyInstance(
                mOriginal.getClass().getClassLoader(),
                mOriginal.getClass().getInterfaces(),
                this
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return onInvoke(proxy, method, args);
        } catch (Throwable t) {
            Logger.e(TAG, "Method " + method.getName() + " failed: " + t.getMessage());
            throw t;
        }
    }

    protected abstract Object onInvoke(Object proxy, Method method, Object[] args) throws Throwable;
}
