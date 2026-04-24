package com.onecore.sdk.core;

import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ProviderInfo;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the instantiation and lifecycle of virtual ContentProviders.
 */
public class ProviderInstaller {
    private static final String TAG = "OneCore-ProviderInstaller";
    private static final Map<String, ContentProvider> sProviders = new HashMap<>();

    public static ContentProvider install(Context virtualContext, ProviderInfo info) {
        String cacheKey = info.packageName + "/" + info.name;
        synchronized (sProviders) {
            if (sProviders.containsKey(cacheKey)) {
                return sProviders.get(cacheKey);
            }

            try {
                Logger.d(TAG, "Installing virtual provider: " + info.name + " for " + info.packageName);
                ClassLoader cl = virtualContext.getClassLoader();
                ContentProvider provider = (ContentProvider) cl.loadClass(info.name).newInstance();

                // Call attachInfo(Context, ProviderInfo)
                try {
                    Method attachInfo = ContentProvider.class.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class);
                    attachInfo.setAccessible(true);
                    attachInfo.invoke(provider, virtualContext, info);
                    Logger.v(TAG, "attachInfo successful for " + info.name);
                } catch (Exception e) {
                    Logger.w(TAG, "attachInfo failed for " + info.name + ", trying context/authority injection");
                    injectContext(provider, virtualContext);
                    injectAuthority(provider, info.authority);
                    provider.onCreate();
                }

                sProviders.put(cacheKey, provider);
                return provider;
            } catch (Exception e) {
                Logger.e(TAG, "Failed to install provider: " + info.name, e);
                return null;
            }
        }
    }

    private static void injectAuthority(ContentProvider provider, String authority) {
        try {
            Field authField = ContentProvider.class.getDeclaredField("mAuthority");
            authField.setAccessible(true);
            authField.set(provider, authority);
        } catch (Exception ignored) {}
    }

    private static void injectContext(ContentProvider provider, Context context) {
        try {
            Field contextField = ContentProvider.class.getDeclaredField("mContext");
            contextField.setAccessible(true);
            contextField.set(provider, context);
        } catch (Exception ignored) {}
    }

    public static ContentProvider getInstalledProvider(String packageName, String className) {
        return sProviders.get(packageName + "/" + className);
    }
}
