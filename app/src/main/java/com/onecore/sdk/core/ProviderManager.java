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

        for (ProviderInfo info : providers) {
            try {
                ContentProvider provider = ProviderInstaller.install(app, info);
                if (provider != null) {
                    registerProviderLocally(provider, info);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Failed to install provider: " + info.name, e);
            }
        }
    }

    private static void registerProviderLocally(ContentProvider provider, ProviderInfo info) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method curAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            curAtMethod.setAccessible(true);
            Object activityThread = curAtMethod.invoke(null);

            if (activityThread == null) return;

            Object transport = getProviderTransport(provider);
            if (transport == null) return;

            // 1. Update mLocalProviders
            Field mLocalProvidersField = activityThread.getClass().getDeclaredField("mLocalProviders");
            mLocalProvidersField.setAccessible(true);
            java.util.Map mLocalProviders = (java.util.Map) mLocalProvidersField.get(activityThread);
            mLocalProviders.put(provider.getClass().getName(), transport);

            // 2. Update mProviderMap
            Field mProviderMapField = activityThread.getClass().getDeclaredField("mProviderMap");
            mProviderMapField.setAccessible(true);
            java.util.Map mProviderMap = (java.util.Map) mProviderMapField.get(activityThread);
            
            if (info.authority != null) {
                String[] auths = info.authority.split(";");
                for (String auth : auths) {
                    mProviderMap.put(auth, transport);
                    // Register in our mapper for redirection help
                    AuthorityMapper.registerAuthority(auth, auth); 
                    Logger.v(TAG, "Authority registered: " + auth);
                }
            }
            
            Logger.i(TAG, "Provider " + info.name + " (" + info.authority + ") registered locally in ActivityThread.");
        } catch (Exception e) {
            Logger.w(TAG, "Failed to register provider in ActivityThread maps: " + e.getMessage());
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
