package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.view.LayoutInflater;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Handles final resource propagation and theme application.
 */
public class ResourceInjector {
    private static final String TAG = "OneCore-ResInjector";

    public static Resources createResources(Context base, String apkPath) {
        try {
            AssetManager assets = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            addAssetPath.invoke(assets, apkPath);

            Resources hostRes = base.getResources();
            return new Resources(assets, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
        } catch (Exception e) {
            Logger.e(TAG, "Failed creating resources", e);
            return null;
        }
    }

    public static void inject(Activity activity, Resources res, int themeRes) {
        try {
            // Force inject into Activity instance
            patchFields(activity, Activity.class, res);
            
            // Fix LayoutInflater
            LayoutInflater inflater = activity.getLayoutInflater();
            patchField(inflater, inflater.getClass(), "mContext", activity);

            // Apply Theme
            if (themeRes != 0) {
                Logger.i(TAG, "Applying target theme: " + themeRes);
                activity.setTheme(themeRes);
            }
            
            Logger.i(TAG, "Resource injection into activity complete.");
        } catch (Exception e) {
            Logger.e(TAG, "Injection FAILED", e);
        }
    }

    private static void patchFields(Object target, Class<?> clazz, Resources res) {
        try {
            Field mResources = clazz.getDeclaredField("mResources");
            mResources.setAccessible(true);
            mResources.set(target, res);
        } catch (Exception ignored) {}
    }

    private static void patchField(Object target, Class<?> clazz, String name, Object val) {
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, val);
        } catch (Exception e) {
            if (clazz.getSuperclass() != null) patchField(target, clazz.getSuperclass(), name, val);
        }
    }
}
