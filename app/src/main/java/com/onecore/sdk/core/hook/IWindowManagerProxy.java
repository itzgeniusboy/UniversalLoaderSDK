package com.onecore.sdk.core.hook;

import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Method;

/**
 * IWindowManager Proxy.
 * Spoofs screen density, size, and rotation.
 */
public class IWindowManagerProxy extends ServiceProxy {

    public IWindowManagerProxy(Object original) {
        super(original);
    }

    @Override
    protected Object onInvoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        
        if ("getInitialDisplayDensity".equals(name)) {
            return 440; // Spoof to a standard device density
        } else if ("getBaseDisplaySize".equals(name)) {
            // Spoof resolution
        }
        
        return method.invoke(mOriginal, args);
    }
}
