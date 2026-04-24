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
        synchronized (sProviders) {
            if (sProviders.containsKey(info.name)) {
                return sProviders.get(info.name);
            }

            try {
                Logger.d(TAG, "Installing virtual provider: " + info.name);
                ClassLoader cl = virtualContext.getClassLoader();
                ContentProvider provider = (ContentProvider) cl.loadClass(info.name).newInstance();

                // Call attachInfo(Context, ProviderInfo)
                try {
                    Method attachInfo = ContentProvider.class.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class);
                    attachInfo.setAccessible(true);
                    attachInfo.invoke(provider, virtualContext, info);
                } catch (Exception e) {
                    Logger.w(TAG, "attachInfo failed for " + info.name + ", trying context injection");
                    injectContext(provider, virtualContext);
                    provider.onCreate();
                }

                sProviders.put(info.name, provider);
                return provider;
            } catch (Exception e) {
                Logger.e(TAG, "Failed to install provider: " + info.name, e);
                return null;
            }
        }
    }

    private static void injectContext(ContentProvider provider, Context context) {
        try {
            Field contextField = ContentProvider.class.getDeclaredField("mContext");
            contextField.setAccessible(true);
            contextField.set(provider, context);
        } catch (Exception ignored) {}
    }

    public static ContentProvider getInstalledProvider(String className) {
        return sProviders.get(className);
    }
}
