package com.onecore.sdk.core.hook;

import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Method;

/**
 * IDisplayManager Proxy.
 * Spoofs display IDs and properties.
 */
public class IDisplayManagerProxy extends ServiceProxy {

    public IDisplayManagerProxy(Object original) {
        super(original);
    }

    @Override
    protected Object onInvoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        
        if ("getDisplayIds".equals(name)) {
            // Return only default display ID to the game
            return new int[]{0};
        }
        
        return method.invoke(mOriginal, args);
    }
}
