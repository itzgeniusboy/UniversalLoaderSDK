package com.onecore.sdk.core;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Deep context and LoadedApk structure manipulation.
 * Ensures the runtime is fully bound to the target APK environment including package identity and resources.
 */
public class ContextFixer {
    private static final String TAG = "OneCore-ContextFixer";

    public static void fix(Context context, String packageName, ClassLoader classLoader, Resources resources) {
        if (context == null) return;
        try {
            Logger.v(TAG, "Applying deep patch to Context: " + context.getClass().getName());
            
            // 1. Patch ContextImpl instance fields
            patchContextImpl(context, packageName, classLoader, resources);
            
            // 2. Patch LoadedApk (mPackageInfo) - CRITICAL for internal framework logic
            patchLoadedApk(context, packageName, classLoader, resources);

        } catch (Exception e) {
            Logger.e(TAG, "CRITICAL: ContextFixer failed for " + packageName, e);
        }
    }

    private static void patchContextImpl(Context context, String pkg, ClassLoader cl, Resources res) {
        Class<?> clazz = context.getClass();
        
        // Basic fields
        setFieldValue(context, clazz, "mResources", res);
        setFieldValue(context, clazz, "mClassLoader", cl);
        setFieldValue(context, clazz, "mBasePackageName", pkg);
        setFieldValue(context, clazz, "mOpPackageName", pkg);
        
        // Assets handling
        try {
            Field mAssets = clazz.getDeclaredField("mAssets");
            mAssets.setAccessible(true);
            mAssets.set(context, res.getAssets());
        } catch (Exception ignored) {}

        // Android 12+ AttributionSource handling
        try {
            Field mAttributionSourceField = clazz.getDeclaredField("mAttributionSource");
            mAttributionSourceField.setAccessible(true);
            Object attributionSource = mAttributionSourceField.get(context);
            if (attributionSource != null) {
                setFieldValue(attributionSource, attributionSource.getClass(), "mPackageName", pkg);
            }
        } catch (Exception ignored) {}
    }

    private static void patchLoadedApk(Context context, String pkg, ClassLoader cl, Resources res) throws Exception {
        Field mPackageInfoField = null;
        try {
            mPackageInfoField = context.getClass().getDeclaredField("mPackageInfo");
        } catch (NoSuchFieldException e) {
            // Some versions might use mPackageInfo in a wrapper or different name
            return;
        }
        
        mPackageInfoField.setAccessible(true);
        Object loadedApk = mPackageInfoField.get(context);
        
        if (loadedApk != null) {
            Class<?> clazz = loadedApk.getClass();
            setFieldValue(loadedApk, clazz, "mPackageName", pkg);
            setFieldValue(loadedApk, clazz, "mClassLoader", cl);
            setFieldValue(loadedApk, clazz, "mResources", res);
            setFieldValue(loadedApk, clazz, "mAssets", res.getAssets());
            
            // Fix ApplicationInfo within LoadedApk to be consistent
            try {
                Field mAppInfoField = clazz.getDeclaredField("mApplicationInfo");
                mAppInfoField.setAccessible(true);
                android.content.pm.ApplicationInfo ai = (android.content.pm.ApplicationInfo) mAppInfoField.get(loadedApk);
                if (ai != null) {
                    ai.packageName = pkg;
                }
            } catch (Exception ignored) {}
        }
    }

    private static void setFieldValue(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            // Recurse into superclasses
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                setFieldValue(target, superclass, fieldName, value);
            }
        }
    }
}
