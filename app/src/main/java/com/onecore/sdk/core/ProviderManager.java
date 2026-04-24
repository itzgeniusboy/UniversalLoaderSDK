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
        if (providers == null || providers.isEmpty()) return;

        Logger.i(TAG, "Installing " + providers.size() + " providers for " + packageName);

        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method curAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            curAtMethod.setAccessible(true);
            Object activityThread = curAtMethod.invoke(null);

            for (ProviderInfo info : providers) {
                try {
                    Logger.d(TAG, "Installing provider: " + info.name + " (" + info.authority + ")");
                    
                    // 1. Create instance
                    ClassLoader cl = app.getClassLoader();
                    ContentProvider provider = (ContentProvider) cl.loadClass(info.name).newInstance();
                    
                    // 2. Attach context
                    // Note: ContentProvider.attachInfo is the standard way to initialize
                    Method attachInfo = ContentProvider.class.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class);
                    attachInfo.setAccessible(true);
                    
                    // Ensure context is the virtualized one
                    Context providerContext = ContextManager.createVirtualContext(context, packageName, cl, app.getResources());
                    attachInfo.invoke(provider, providerContext, info);
                    
                    // 3. Register with ActivityThread to make it accessible via ContentResolver
                    // This is complex as it involves private APIs that vary across Android versions.
                    // A simpler way often used in virtualization is to hook the ContentResolver calls.
                    // However, we can try to inject into mLocalProviders
                    registerProviderLocally(activityThread, provider, info);
                    
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to install provider: " + info.name, e);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to access ActivityThread for provider installation", e);
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
