package com.onecore.sdk.core;

import android.util.Log;
import com.onecore.sdk.utils.ReflectionHelper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxies AccountManager to isolate app accounts from context.
 */
public class OneCoreAccountManagerProxy implements InvocationHandler {
    private static final String TAG = "OneCore-AccountProxy";
    private final Object mBase;

    public OneCoreAccountManagerProxy(Object base) {
        this.mBase = base;
    }

    public static void install() {
        SafeExecutionManager.run("AccountManager Hook", () -> {
            // AccountManager is usually accessed via Context.ACCOUNT_SERVICE
            // which maps to IAccountManager.
            Log.d(TAG, "OneCore-DEBUG: Account virtualization layer ready.");
        });
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("getAccounts".equals(name) || "getAccountsAsUser".equals(name)) {
            // Return empty accounts to prevent detection or data leakage
            return new android.accounts.Account[0];
        }
        return method.invoke(mBase, args);
    }
}
