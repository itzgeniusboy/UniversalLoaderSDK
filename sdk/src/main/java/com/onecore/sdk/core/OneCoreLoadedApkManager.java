package com.onecore.sdk.core;

import android.content.Context;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Manages the creation and injection of LoadedApk objects into ActivityThread.
 */
public class OneCoreLoadedApkManager {
    private static final String TAG = "OneCore-LoadedApk";
    private static final Map<String, WeakReference<Object>> mLoadedApkCache = new java.util.HashMap<>();

    public static Object getLoadedApk(Context context, String apkPath, String packageName, ClassLoader classLoader, android.content.res.Resources resources) {
        if (mLoadedApkCache.containsKey(packageName)) {
            Object obj = mLoadedApkCache.get(packageName).get();
            if (obj != null) return obj;
        }

        try {
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
            
            if (loadedApk != null) {
                // Patch the new LoadedApk
                setField(loadedApk.getClass(), loadedApk, "mClassLoader", classLoader);
                setField(loadedApk.getClass(), loadedApk, "mResources", resources);
                setField(loadedApk.getClass(), loadedApk, "mPackageName", packageName);
                setField(loadedApk.getClass(), loadedApk, "mAppInfo", ai);
                
                // Inject into ActivityThread.mPackages
                mPackages.put(packageName, new WeakReference<>(loadedApk));
                mLoadedApkCache.put(packageName, new WeakReference<>(loadedApk));
                
                Log.i(TAG, "OneCore-DEBUG: LoadedApk injected");
            }
            
            return loadedApk;
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: LoadedApk injection FAILED !!!", e);
            return null;
        }
    }

    private static void setField(Class<?> clazz, Object obj, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
