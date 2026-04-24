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
                    
                    Method attachInfo = ContentProvider.class.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class);
                    attachInfo.setAccessible(true);
                    
                    attachInfo.invoke(provider, app, info);
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
            // This is a simplified internal registration. 
            // Real ActivityThread handles providers via installContentProviders()
            // which populates multiple maps.
            
            // On most versions, we only need to hook the ContentResolver used by the app.
        } catch (Exception ignored) {}
    }
}
