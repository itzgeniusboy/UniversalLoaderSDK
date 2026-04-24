package com.onecore.sdk.core;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Method;

/**
 * Handles manual Resource creation and AssetManager manipulation.
 * Resolves the "Resource Not Found" and "Black Screen" issues.
 */
public class ResourceInjector {
    private static final String TAG = "OneCore-ResInjector";

    public static Resources createResources(Context hostContext, String apkPath) {
        try {
            Logger.d(TAG, "Creating custom Resources for APK: " + apkPath);
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            addAssetPath.invoke(assetManager, apkPath);

            Resources hostRes = hostContext.getResources();
            Resources guestRes = new Resources(assetManager, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
            
            Logger.i(TAG, "Custom Resources created successfully.");
            return guestRes;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to create custom Resources", e);
            return null;
        }
    }

    /**
     * Injects resources into a specific context object.
     */
    public static void inject(Context context, Resources resources) {
        try {
            java.lang.reflect.Field mResources = context.getClass().getDeclaredField("mResources");
            mResources.setAccessible(true);
            mResources.set(context, resources);
            
            java.lang.reflect.Field mTheme = context.getClass().getDeclaredField("mTheme");
            mTheme.setAccessible(true);
            mTheme.set(context, null); // Force theme re-calculation if needed
        } catch (Exception ignored) {}
    }
}
