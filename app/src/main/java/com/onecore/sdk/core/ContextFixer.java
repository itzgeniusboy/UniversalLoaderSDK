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
            // Process the context and its base contexts if it's a wrapper
            Context current = context;
            while (current instanceof android.content.ContextWrapper) {
                Context base = ((android.content.ContextWrapper) current).getBaseContext();
                if (base == null || base == current) break;
                current = base;
            }

            Logger.v(TAG, "Applying deep patch to Context: " + current.getClass().getName());
            
            // 1. Patch ContextImpl instance fields
            patchContextImpl(current, packageName, classLoader, resources);
            
            // 2. Patch LoadedApk (mPackageInfo) - CRITICAL for internal framework logic
            patchLoadedApk(current, packageName, classLoader, resources);

        } catch (Exception e) {
            Logger.e(TAG, "CRITICAL: ContextFixer failed for " + packageName, e);
        }
    }

    private static void patchContextImpl(Context context, String pkg, ClassLoader cl, Resources res) {
        Class<?> clazz = context.getClass();
        
        // Ensure we are working with ContextImpl directly if possible
        if (!clazz.getName().equals("android.app.ContextImpl")) {
            // Search for mBase in wrappers
            try {
                Field mBase = getField(clazz, "mBase");
                if (mBase != null) {
                    Context base = (Context) mBase.get(context);
                    if (base != null) patchContextImpl(base, pkg, cl, res);
                }
            } catch (Exception ignored) {}
        }

        setFieldValue(context, clazz, "mResources", res);
        setFieldValue(context, clazz, "mClassLoader", cl);
        setFieldValue(context, clazz, "mBasePackageName", pkg);
        setFieldValue(context, clazz, "mOpPackageName", pkg);
        
        // Android 10+ identity
        try {
            setFieldValue(context, clazz, "mOpPackageName", pkg);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // AttributionSource handling is already in patchContextImpl but let's double check
            }
        } catch (Exception ignored) {}
        
        // PackageManager patch
        try {
            Field mPackageManager = getField(clazz, "mPackageManager");
            if (mPackageManager != null) {
                // We could set a fake PackageManager instance here if needed
                // For now system uses ActivityThread.getPackageManager() which we hooked.
            }
        } catch (Exception ignored) {}
        
        // Assets handling
        try {
            Field mAssets = getField(clazz, "mAssets");
            if (mAssets != null) {
                mAssets.set(context, res.getAssets());
            }
        } catch (Exception ignored) {}

        // ContentResolver patch (important for setting package name in queries)
        try {
            Field mContentResolver = getField(clazz, "mContentResolver");
            if (mContentResolver != null) {
                Object resolver = mContentResolver.get(context);
                if (resolver != null) {
                    setFieldValue(resolver, resolver.getClass(), "mPackageName", pkg);
                }
            }
        } catch (Exception ignored) {}

        // Storage redirection
        try {
            setFieldValue(context, clazz, "mFilesDir", VirtualStorage.getVirtualDir(pkg, "files"));
            setFieldValue(context, clazz, "mCacheDir", VirtualStorage.getVirtualDir(pkg, "cache"));
        } catch (Exception ignored) {}

        // Android 12+ AttributionSource handling
        try {
            Field mAttributionSourceField = getField(clazz, "mAttributionSource");
            if (mAttributionSourceField != null) {
                Object attributionSource = mAttributionSourceField.get(context);
                if (attributionSource != null) {
                    setFieldValue(attributionSource, attributionSource.getClass(), "mPackageName", pkg);
                }
            }
        } catch (Exception ignored) {}
    }

    private static Field getField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return getField(superclass, fieldName);
            }
        }
        return null;
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
