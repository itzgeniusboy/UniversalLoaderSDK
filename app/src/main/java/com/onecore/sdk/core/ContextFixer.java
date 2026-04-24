package com.onecore.sdk.core;

import android.content.Context;
import android.content.res.Resources;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;

/**
 * Handles deep patching of ContextImpl and LoadedApk structures.
 * Ensures the runtime thinks it is the guest application.
 */
public class ContextFixer {
    private static final String TAG = "OneCore-ContextFixer";

    public static void fix(Context context, String guestPackageName, ClassLoader guestClassLoader, Resources guestResources) {
        if (context == null) return;
        
        try {
            Logger.d(TAG, "Fixing Context for package: " + guestPackageName);

            // 1. Patch ContextImpl
            Class<?> contextImplClass = context.getClass();
            
            setField(context, contextImplClass, "mResources", guestResources);
            setField(context, contextImplClass, "mBasePackageName", guestPackageName);
            setField(context, contextImplClass, "mOpPackageName", guestPackageName);
            
            // 2. Patch LoadedApk (The most important part)
            Field mPackageInfoField = contextImplClass.getDeclaredField("mPackageInfo");
            mPackageInfoField.setAccessible(true);
            Object loadedApk = mPackageInfoField.get(context);
            
            if (loadedApk != null) {
                Logger.d(TAG, "Patching LoadedApk associated with Context.");
                Class<?> loadedApkClass = loadedApk.getClass();
                
                setField(loadedApk, loadedApkClass, "mPackageName", guestPackageName);
                setField(loadedApk, loadedApkClass, "mResources", guestResources);
                setField(loadedApk, loadedApkClass, "mClassLoader", guestClassLoader);
                
                // Optional: mAppDir, mResDir etc can also be patched if needed
            }

            Logger.i(TAG, "Context fix applied successfully.");
        } catch (Exception e) {
            Logger.e(TAG, "Context fix failed", e);
        }
    }

    private static void setField(Object obj, Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (NoSuchFieldException e) {
            // Try parent classes
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                setField(obj, superclass, fieldName, value);
            }
        } catch (Exception e) {
            Logger.w(TAG, "Failed to set field " + fieldName + ": " + e.getMessage());
        }
    }
}
