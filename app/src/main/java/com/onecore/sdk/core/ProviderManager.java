package com.onecore.sdk.core;

import android.app.Application;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ProviderInfo;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Manages ContentProvider installation and lifecycle within the virtual environment.
 */
public class ProviderManager {
    private static final String TAG = "OneCore-ProviderMgr";

    public static void installProviders(Context context, Application app, String packageName, List<ProviderInfo> providers) {
        if (providers == null || providers.isEmpty() || app == null) return;

        Logger.i(TAG, "Installing " + providers.size() + " providers for " + packageName);

        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method curAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            curAtMethod.setAccessible(true);
            Object activityThread = curAtMethod.invoke(null);

            if (activityThread == null) return;

            // ActivityThread.installContentProviders(Context, List<ProviderInfo>)
            Method installMethod = null;
            try {
                installMethod = atClass.getDeclaredMethod("installContentProviders", Context.class, List.class);
                installMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                // Older versions or different signature
            }

            if (installMethod != null) {
                installMethod.invoke(activityThread, app, providers);
                Logger.d(TAG, "System installContentProviders called successfully");
                return;
            }

            // Manual fallback if installation method is not found
            for (ProviderInfo info : providers) {
                try {
                    Logger.d(TAG, "Installing provider manually: " + info.name);
                    ClassLoader cl = app.getClassLoader();
                    ContentProvider provider = (ContentProvider) cl.loadClass(info.name).newInstance();
                    
                    // Attach context accurately
                    try {
                        Method attachInfo = ContentProvider.class.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class);
                        attachInfo.setAccessible(true);
                        attachInfo.invoke(provider, app, info);
                        Logger.v(TAG, "attachInfo successful for " + info.name);
                    } catch (Exception e) {
                        Logger.w(TAG, "Standard attachInfo failed for " + info.name + ", attempting fallback");
                        // Fallback: manually set mContext if possible or just call onCreate
                        try {
                            Field contextField = ContentProvider.class.getDeclaredField("mContext");
                            contextField.setAccessible(true);
                            contextField.set(provider, app);
                        } catch (Exception ignored) {}
                        provider.onCreate();
                    }
                    
                    registerProviderLocally(activityThread, provider, info);
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to install provider manually: " + info.name, e);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed during provider installation", e);
        }
    }

    private static void registerProviderLocally(Object activityThread, ContentProvider provider, ProviderInfo info) {
        try {
            // In Android, ActivityThread maintains mLocalProviders and mProviderMap
            // We want to insert our virtual provider here so local ContentResolver.acquireProvider() finds it.
            
            Field mLocalProvidersField = activityThread.getClass().getDeclaredField("mLocalProviders");
            mLocalProvidersField.setAccessible(true);
            java.util.Map mLocalProviders = (java.util.Map) mLocalProvidersField.get(activityThread);
            
            // On newer Android, the key is usually an IBinder (represented by the provider's token)
            // But for simplicity, many versions use the ProviderInfo.authority or similar.
            
            // We need a proper IContentProvider proxy for the local map.
            Object transport = getProviderTransport(provider);
            if (transport != null) {
                mLocalProviders.put(provider.getClass().getName(), transport);
                Logger.i(TAG, "Provider " + info.name + " (" + info.authority + ") installed in ActivityThread.");
            }
        } catch (Exception e) {
            Logger.w(TAG, "Failed to register provider locally: " + e.getMessage());
        }
    }

    private static Object getProviderTransport(ContentProvider provider) {
        try {
            // ContentProvider.getIContentProvider()
            Method getIContentProvider = ContentProvider.class.getDeclaredMethod("getIContentProvider");
            getIContentProvider.setAccessible(true);
            return getIContentProvider.invoke(provider);
        } catch (Exception e) {
            return null;
        }
    }
}
