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

            String[] authorities = info.authority.split(";");

            // 1. Create ProviderClientRecord if possible (Android 4.0+)
            Object pcRecord = null;
            try {
                Class<?> pcrClass = Class.forName("android.app.ActivityThread$ProviderClientRecord");
                java.lang.reflect.Constructor<?> constructor = pcrClass.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                
                // Signatures vary:
                // Newer: (String[] names, IContentProvider provider, ContentProvider localProvider, ContentProviderHolder holder)
                // Older: (String name, IContentProvider provider, ContentProvider localProvider)
                if (constructor.getParameterTypes().length >= 4) {
                    pcRecord = constructor.newInstance(authorities, transport, provider, null);
                } else {
                    pcRecord = constructor.newInstance(info.authority, transport, provider);
                }
            } catch (Exception e) {
                Logger.w(TAG, "Could not create ProviderClientRecord, using transport directly");
            }

            Object mapValue = (pcRecord != null) ? pcRecord : transport;

            // 2. Update mLocalProviders (IBinder -> PCRecord/Transport)
            try {
                Field mLocalProvidersField = atClass.getDeclaredField("mLocalProviders");
                mLocalProvidersField.setAccessible(true);
                java.util.Map mLocalProviders = (java.util.Map) mLocalProvidersField.get(activityThread);
                mLocalProviders.put(transport instanceof android.os.IBinder ? transport : provider.getClass().getName(), mapValue);
            } catch (Exception e) {
                Logger.w(TAG, "mLocalProviders update failed");
            }

            // 3. Update mProviderMap (ProviderKey/String -> PCRecord/Transport)
            try {
                Field mProviderMapField = atClass.getDeclaredField("mProviderMap");
                mProviderMapField.setAccessible(true);
                java.util.Map mProviderMap = (java.util.Map) mProviderMapField.get(activityThread);
                
                for (String auth : authorities) {
                    // Try direct string key (older)
                    mProviderMap.put(auth, mapValue);
                    
                    // Try ProviderKey (newer Android)
                    try {
                        Class<?> pkClass = Class.forName("android.app.ActivityThread$ProviderKey");
                        java.lang.reflect.Constructor<?> pkCons = pkClass.getDeclaredConstructor(String.class, int.class);
                        pkCons.setAccessible(true);
                        Object pk = pkCons.newInstance(auth, android.os.Process.myUserHandle().hashCode());
                        mProviderMap.put(pk, mapValue);
                    } catch (Exception ignored) {}

                    // Register in our mapper for redirection help
                    AuthorityMapper.registerAuthority(auth, auth); 
                }
            } catch (Exception e) {
                Logger.w(TAG, "mProviderMap update failed");
            }

            // 4. Update mLocalProvidersByName (String/ComponentName -> PCRecord/Transport)
            try {
                Field mLocalProvidersByNameField = atClass.getDeclaredField("mLocalProvidersByName");
                mLocalProvidersByNameField.setAccessible(true);
                java.util.Map mLocalProvidersByName = (java.util.Map) mLocalProvidersByNameField.get(activityThread);
                for (String auth : authorities) {
                    mLocalProvidersByName.put(auth, mapValue);
                }
                
                // Also ComponentName if expected
                android.content.ComponentName cn = new android.content.ComponentName(info.packageName, info.name);
                mLocalProvidersByName.put(cn, mapValue);
            } catch (Exception ignored) {}

            Logger.i(TAG, "Provider " + info.name + " (" + info.authority + ") registered locally in ActivityThread.");
        } catch (Exception e) {
            Logger.e(TAG, "Critical: Provider registration FAILED: " + e.getMessage(), e);
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
