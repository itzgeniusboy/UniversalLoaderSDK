package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import java.lang.reflect.Field;

/**
 * Utility to fix ContextImpl and LoadedApk fields for virtualization.
 */
public class ContextFixer {
    private static final String TAG = "OneCoreContextFixer";

    public static void fixContext(Context context, String packageName) {
        if (context == null) return;
        
        try {
            Log.i(TAG, "OneCore-DEBUG: Context fixing for [" + packageName + "]");
            
            // 1. Fix ContextImpl fields
            patchContextImpl(context, packageName);
            
            // 2. Fix LoadedApk (mPackageInfo)
            patchLoadedApk(context);
            
            // 3. Specifically for Activities, nullify the cached LayoutInflater
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

    private static void patchContextImpl(Context context, String packageName) {
        try {
            // Find the ContextImpl instance
            Context baseContext = context;
            while (baseContext instanceof android.content.ContextWrapper) {
                baseContext = ((android.content.ContextWrapper) baseContext).getBaseContext();
            }

            Class<?> contextImplClass = baseContext.getClass();
            
            // Fixed Package Names
            setField(contextImplClass, baseContext, "mPackageName", packageName);
            setField(contextImplClass, baseContext, "mBasePackageName", packageName);
            try {
                setField(contextImplClass, baseContext, "mOpPackageName", packageName);
            } catch (Exception ignored) {}

            // Fixed Resources
            Resources virtualRes = VirtualContainer.getInstance().getResources();
            if (virtualRes != null) {
                setField(contextImplClass, baseContext, "mResources", virtualRes);
                // Clear mInflater to force re-creation with guest Resources
                try {
                    setField(contextImplClass, baseContext, "mInflater", null);
                } catch (Exception ignored) {}
            }

            // Fix mPackageInfo to point to the injected LoadedApk
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
                currentActivityThreadMethod.setAccessible(true);
                Object activityThread = currentActivityThreadMethod.invoke(null);

                // Patch mPackageManager if it exists in ContextImpl
                try {
                    Field sPmField = activityThreadClass.getDeclaredField("sPackageManager");
                    sPmField.setAccessible(true);
                    Object sPm = sPmField.get(null);
                    setField(contextImplClass, baseContext, "mPackageManager", sPm);
                } catch (Exception ignored) {}

                Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
                mPackagesField.setAccessible(true);
                java.util.Map mPackages = (java.util.Map) mPackagesField.get(activityThread);
                Object ref = mPackages.get(packageName);
                if (ref != null) {
                    Method getMethod = ref.getClass().getMethod("get");
                    Object loadedApk = getMethod.invoke(ref);
                    if (loadedApk != null) {
                        setField(contextImplClass, baseContext, "mPackageInfo", loadedApk);
                        
                        // Patch Application instance
                        android.app.Application virtualApp = VirtualContainer.getInstance().getTargetApplication();
                        if (virtualApp != null) {
                            setField(contextImplClass, baseContext, "mApplication", virtualApp);
                            setField(loadedApk.getClass(), loadedApk, "mApplication", virtualApp);
                        }
                        
                        Log.d(TAG, "OneCore-DEBUG: ContextImpl.mPackageInfo + mApplication patched.");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed during deep context patching", e);
            }
            
            Log.d(TAG, "ContextImpl fields patched.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch ContextImpl", e);
        }
    }

    private static void patchLoadedApk(Context context) {
        try {
            Context baseContext = context;
            while (baseContext instanceof android.content.ContextWrapper) {
                baseContext = ((android.content.ContextWrapper) baseContext).getBaseContext();
            }

            Field mPackageInfoField = baseContext.getClass().getDeclaredField("mPackageInfo");
            mPackageInfoField.setAccessible(true);
            Object mPackageInfo = mPackageInfoField.get(baseContext);

            if (mPackageInfo == null) {
                Log.w(TAG, "mPackageInfo is null, cannot patch LoadedApk");
                return;
            }

            Class<?> loadedApkClass = mPackageInfo.getClass();
            
            // mClassLoader
            setField(loadedApkClass, mPackageInfo, "mClassLoader", VirtualContainer.getInstance().getClassLoader());
            
            // mResources
            Resources virtualRes = VirtualContainer.getInstance().getResources();
            if (virtualRes != null) {
                setField(loadedApkClass, mPackageInfo, "mResources", virtualRes);
            }

            Log.d(TAG, "LoadedApk fields patched.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch LoadedApk", e);
        }
    }

    private static void setField(Class<?> clazz, Object obj, String fieldName, Object value) throws Exception {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (NoSuchFieldException e) {
            // Some fields might not exist on all Android versions
            Log.v(TAG, "Field " + fieldName + " not found in " + clazz.getName());
        }
    }
}
