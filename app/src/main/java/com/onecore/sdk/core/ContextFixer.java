package com.onecore.sdk.core;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Deep context and LoadedApk structure manipulation.
 * Ensures the runtime is fully bound to the target APK environment.
 */
public class ContextFixer {
    private static final String TAG = "OneCore-ContextFixer";

    public static void fix(Context context, String packageName, ClassLoader classLoader, Resources resources) {
        if (context == null) return;
        try {
            Logger.d(TAG, "Deep fixing Context for: " + packageName);
            
            // 1. Patch ContextImpl instance fields
            patchContextImpl(context, packageName, classLoader, resources);
            
            // 2. Patch LoadedApk (mPackageInfo) - CRITICAL for internal logic
            patchLoadedApk(context, packageName, classLoader, resources);

            Logger.i(TAG, "Deep context fix complete.");
        } catch (Exception e) {
            Logger.e(TAG, "CRITICAL: ContextFixer failed", e);
        }
    }

    private static void patchContextImpl(Context context, String pkg, ClassLoader cl, Resources res) {
        Class<?> clazz = context.getClass();
        setFieldValue(context, clazz, "mResources", res);
        setFieldValue(context, clazz, "mClassLoader", cl);
        setFieldValue(context, clazz, "mBasePackageName", pkg);
        setFieldValue(context, clazz, "mOpPackageName", pkg);
        
        try {
            Field mAssets = clazz.getDeclaredField("mAssets");
            mAssets.setAccessible(true);
            mAssets.set(context, res.getAssets());
        } catch (Exception ignored) {}
    }

    private static void patchLoadedApk(Context context, String pkg, ClassLoader cl, Resources res) throws Exception {
        Field mPackageInfoField = context.getClass().getDeclaredField("mPackageInfo");
        mPackageInfoField.setAccessible(true);
        Object loadedApk = mPackageInfoField.get(context);
        
        if (loadedApk != null) {
            Class<?> clazz = loadedApk.getClass();
            setFieldValue(loadedApk, clazz, "mPackageName", pkg);
            setFieldValue(loadedApk, clazz, "mClassLoader", cl);
            setFieldValue(loadedApk, clazz, "mResources", res);
            setFieldValue(loadedApk, clazz, "mAssets", res.getAssets());
            
            // Fix PackageInfo within LoadedApk
            Field mAppInfoField = clazz.getDeclaredField("mApplicationInfo");
            mAppInfoField.setAccessible(true);
            android.content.pm.ApplicationInfo ai = (android.content.pm.ApplicationInfo) mAppInfoField.get(loadedApk);
            if (ai != null) {
                ai.packageName = pkg;
                ai.uid = android.os.Process.myUid();
            }
        }
    }

    private static void setFieldValue(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            try {
                Field field = clazz.getSuperclass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception ignored) {}
        }
    }
}
