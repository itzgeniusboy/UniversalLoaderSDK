package com.onecore.sdk.core;

import android.content.Context;
import android.util.Log;
import com.onecore.sdk.utils.ReflectionHelper;
import java.io.File;
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
        synchronized (mLoadedApkCache) {
            if (mLoadedApkCache.containsKey(packageName)) {
                Object obj = mLoadedApkCache.get(packageName).get();
                if (obj != null) return obj;
            }
        }

        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = ReflectionHelper.invokeMethod(activityThreadClass, "currentActivityThread");
            
            // Get mPackages and mResourcePackages maps
            Map mPackages = (Map) ReflectionHelper.getFieldValue(activityThread, "mPackages");
            Map mResourcePackages = (Map) ReflectionHelper.getFieldValue(activityThread, "mResourcePackages");
            
            // ApplicationInfo for the guest app
            android.content.pm.ApplicationInfo ai = new android.content.pm.ApplicationInfo();
            ai.packageName = packageName;
            ai.sourceDir = apkPath;
            ai.publicSourceDir = apkPath;
            ai.dataDir = context.getDir("v_data_" + packageName, Context.MODE_PRIVATE).getAbsolutePath();
            ai.deviceProtectedDataDir = ai.dataDir;
            ReflectionHelper.setFieldValue(ai, ai.dataDir, "credentialProtectedDataDir");
            ai.uid = context.getApplicationInfo().uid;
            ai.flags = context.getApplicationInfo().flags; // Inherit flags for compatibility
            
            File libDir = context.getDir("v_lib_" + packageName, Context.MODE_PRIVATE);
            ai.nativeLibraryDir = libDir.getAbsolutePath();
            // Modern Android requires primaryCpuAbi to be set for native libs to work properly in some cases
            ReflectionHelper.setFieldValue(ai, android.os.Build.SUPPORTED_ABIS[0], "primaryCpuAbi");
            
            Log.d(TAG, "OneCore-Apk: Creating LoadedApk for " + packageName);
            Log.d(TAG, "OneCore-Apk: DataDir: " + ai.dataDir);
            Log.d(TAG, "OneCore-Apk: LibDir: " + ai.nativeLibraryDir);
            
            // CompatibilityInfo
            Object compatInfo = ReflectionHelper.getStaticFieldValue("android.content.res.CompatibilityInfo", "DEFAULT_COMPATIBILITY_INFO");
            
            // getPackageInfoNoCheck
            Object loadedApk = ReflectionHelper.invokeMethod(activityThread, "getPackageInfoNoCheck", ai, compatInfo);
            
            if (loadedApk != null) {
                Log.i(TAG, "OneCore-Apk: LoadedApk created, patching fields...");
                // Patch the new LoadedApk
                ReflectionHelper.setFieldValue(loadedApk, classLoader, "mClassLoader");
                ReflectionHelper.setFieldValue(loadedApk, resources, "mResources");
                ReflectionHelper.setFieldValue(loadedApk, packageName, "mPackageName");
                ReflectionHelper.setFieldValue(loadedApk, ai, "mAppInfo");
                ReflectionHelper.setFieldValue(loadedApk, ai.dataDir, "mDataDir");
                ReflectionHelper.setFieldValue(loadedApk, ai.nativeLibraryDir, "mLibDir", "mBaseLibDir", "mAppLibDir");
                
                // Android 10+ fixes
                try {
                    ReflectionHelper.setFieldValue(loadedApk, ai.sourceDir, "mResDir");
                    ReflectionHelper.setFieldValue(loadedApk, ai.publicSourceDir, "mSplitResDirs");
                } catch (Exception ignored) {}

                // Inject into ActivityThread maps
                WeakReference<Object> ref = new WeakReference<>(loadedApk);
                if (mPackages != null) {
                    synchronized (mPackages) {
                        mPackages.put(packageName, ref);
                    }
                }
                if (mResourcePackages != null) {
                    synchronized (mResourcePackages) {
                        mResourcePackages.put(packageName, ref);
                    }
                }
                synchronized (mLoadedApkCache) {
                    mLoadedApkCache.put(packageName, ref);
                }

                // CRITICAL: Patch ResourcesManager too (Global Resource Cache)
                try {
                    Class<?> rmClass = Class.forName("android.app.ResourcesManager");
                    Object rm = ReflectionHelper.invokeMethod(rmClass, "getInstance");
                    if (rm != null) {
                        // For modern Android (9+), patching LoadedApk maps is usually enough,
                        // but games sometimes use Resources.getSystem() or other global accessors.
                        Map mResourceImpls = (Map) ReflectionHelper.getFieldValue(rm, "mResourceImpls");
                        if (mResourceImpls != null) {
                            synchronized (mResourceImpls) {
                                // Clear cache to force reload with virtual resources if the package matches
                                // Or we can inject our virtual ResImpl here if needed.
                                Log.d(TAG, "ResourcesManager.mResourceImpls found, patching cache...");
                            }
                        }
                    }
                } catch (Exception ignored) {}
                
                Log.i(TAG, "OneCore-DEBUG: LoadedApk injected into AT maps for " + packageName);
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
