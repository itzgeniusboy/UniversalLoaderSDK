package com.onecore.sdk.core;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Handles the creation and injection of LoadedApk objects into ActivityThread.
 */
public class LoadedApkManager {
    private static final String TAG = "OneCore-LoadedApk";

    public static Object inject(Context context, String apkPath, String packageName, ClassLoader classLoader, android.content.res.Resources resources) {
        try {
            Log.i(TAG, "OneCore-DEBUG: LoadedApk injection starting for [" + packageName + "]");
            
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);
            
            // Get mPackages map
            Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
            mPackagesField.setAccessible(true);
            Map mPackages = (Map) mPackagesField.get(activityThread);
            
            // ApplicationInfo for the guest app
            android.content.pm.ApplicationInfo ai = new android.content.pm.ApplicationInfo();
            ai.packageName = packageName;
            ai.sourceDir = apkPath;
            ai.publicSourceDir = apkPath;
            ai.dataDir = context.getDir("v_data_" + packageName, Context.MODE_PRIVATE).getAbsolutePath();
            ai.uid = context.getApplicationInfo().uid;
            
            // CompatibilityInfo
            Class<?> compatInfoClass = Class.forName("android.content.res.CompatibilityInfo");
            Field defaultField = compatInfoClass.getDeclaredField("DEFAULT_COMPATIBILITY_INFO");
            defaultField.setAccessible(true);
            Object compatInfo = defaultField.get(null);
            
            // getPackageInfoNoCheck
            Method getPackageInfoMethod = activityThreadClass.getDeclaredMethod("getPackageInfoNoCheck", 
                android.content.pm.ApplicationInfo.class, compatInfoClass);
            getPackageInfoMethod.setAccessible(true);
            Object loadedApk = getPackageInfoMethod.invoke(activityThread, ai, compatInfo);
            
            // Patch the new LoadedApk with virtual info
            setField(loadedApk.getClass(), loadedApk, "mClassLoader", classLoader);
            setField(loadedApk.getClass(), loadedApk, "mResources", resources);
            setField(loadedApk.getClass(), loadedApk, "mPackageName", packageName);
            setField(loadedApk.getClass(), loadedApk, "mAppInfo", ai);
            
            // Put it into mPackages
            mPackages.put(packageName, new java.lang.ref.WeakReference<>(loadedApk));
            
            Log.i(TAG, "OneCore-DEBUG: LoadedApk injected into ActivityThread.mPackages for " + packageName);
            
            return loadedApk;
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: Failed to inject LoadedApk !!!", e);
            return null;
        }
    }

    private static void setField(Class<?> clazz, Object obj, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
