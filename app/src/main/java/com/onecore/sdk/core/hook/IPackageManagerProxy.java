package com.onecore.sdk.core.hook;

import com.onecore.sdk.core.VirtualPackageManager;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Method;

/**
 * IPackageManager Proxy.
 * Spoofs package info, permissions, and signatures.
 */
public class IPackageManagerProxy extends ServiceProxy {

    public IPackageManagerProxy(Object original) {
        super(original);
    }

    @Override
    protected Object onInvoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if ("getPackageInfo".equals(name)) {
            String pkgName = (String) args[0];
            return VirtualPackageManager.getInstance().getPackageInfo(pkgName, (int) args[1]);
        } else if ("getApplicationInfo".equals(name)) {
             String pkgName = (String) args[0];
             return VirtualPackageManager.getInstance().getApplicationInfo(pkgName, (int) args[1]);
        } else if ("checkPermission".equals(name)) {
            // Always return PERMISSION_GRANTED for virtual app internally
            return 0; // android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        return method.invoke(mOriginal, args);
    }
}
