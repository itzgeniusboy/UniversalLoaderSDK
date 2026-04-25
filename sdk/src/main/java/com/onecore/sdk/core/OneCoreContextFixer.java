package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utility to fix ContextImpl and LoadedApk fields for virtualization.
 */
public class OneCoreContextFixer {
    private static final String TAG = "OneCore-ContextFixer";

    public static void fixContext(Context context, String packageName) {
        if (context == null) return;
        
        try {
            // Find the ContextImpl instance
            Context baseContext = context;
            while (baseContext instanceof android.content.ContextWrapper) {
                baseContext = ((android.content.ContextWrapper) baseContext).getBaseContext();
            }

            Class<?> contextImplClass = baseContext.getClass();
            
            // 1. Fix Package Names
            setField(contextImplClass, baseContext, "mPackageName", packageName);
            setField(contextImplClass, baseContext, "mBasePackageName", packageName);
            try {
                setField(contextImplClass, baseContext, "mOpPackageName", packageName);
            } catch (Exception ignored) {}

            // 2. Fix Resources
            Resources virtualRes = VirtualContainer.getInstance().getResources();
            if (virtualRes != null) {
                setField(contextImplClass, baseContext, "mResources", virtualRes);
                // Clear LayoutInflater cache
                try {
                    setField(contextImplClass, baseContext, "mInflater", null);
                } catch (Exception ignored) {}
            }

            // 3. Fix mPackageInfo (LoadedApk)
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
                currentActivityThreadMethod.setAccessible(true);
                Object activityThread = currentActivityThreadMethod.invoke(null);

                Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
                mPackagesField.setAccessible(true);
                java.util.Map mPackages = (java.util.Map) mPackagesField.get(activityThread);
                Object ref = mPackages.get(packageName);
                if (ref instanceof java.lang.ref.WeakReference) {
                    Object loadedApk = ((java.lang.ref.WeakReference) ref).get();
                    if (loadedApk != null) {
                        setField(contextImplClass, baseContext, "mPackageInfo", loadedApk);
                        
                        // Fix Application
                        android.app.Application virtualApp = VirtualContainer.getInstance().getTargetApplication();
                        if (virtualApp != null) {
                            setField(contextImplClass, baseContext, "mApplication", virtualApp);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed deep LoadedApk link for context");
            }

            // 4. Activity specific fixes
            if (context instanceof Activity) {
                try {
                    setField(Activity.class, context, "mInflater", null);
                } catch (Exception ignored) {}
            }
            
            Log.i(TAG, "OneCore-DEBUG: Resources switched");
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: Context fix FAILED !!!", e);
        }
    }

    private static void setField(Class<?> clazz, Object obj, String fieldName, Object value) throws Exception {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (NoSuchFieldException e) {
            // Field might not exist on this version
        }
    }
}
