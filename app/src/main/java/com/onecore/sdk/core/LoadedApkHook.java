package com.onecore.sdk.core;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import com.onecore.sdk.utils.Logger;

public class LoadedApkHook {
    private static final String TAG = "LoadedApkHook";

    public static void hook(String packageName, ClassLoader appClassLoader) {
        try {
            Object activityThread = getActivityThread();
            if (activityThread == null) return;

            Field mPackagesField = activityThread.getClass().getDeclaredField("mPackages");
            mPackagesField.setAccessible(true);
            Map<String, WeakReference<?>> mPackages = (Map<String, WeakReference<?>>) mPackagesField.get(activityThread);

            // 1. Get ApplicationInfo for guest
            android.content.pm.PackageInfo guestPackage = com.onecore.sdk.core.pm.VirtualPackageManager.get().getClonedPackage(packageName);
            if (guestPackage == null || guestPackage.applicationInfo == null) {
                Logger.e(TAG, "Cannot hook LoadedApk: Package metadata missing for " + packageName);
                return;
            }

            android.content.pm.ApplicationInfo guestAi = guestPackage.applicationInfo;

            // 2. Build or hijacking a LoadedApk
            Method getPackageInfoMethod = null;
            try {
                getPackageInfoMethod = activityThread.getClass().getDeclaredMethod("getPackageInfoNoCheck", android.content.pm.ApplicationInfo.class, Class.forName("android.content.res.CompatibilityInfo"));
            } catch (Exception e) {
                Logger.w(TAG, "Standard getPackageInfoNoCheck not found, trying variants...");
            }

            Object loadedApk = null;
            if (getPackageInfoMethod != null) {
                getPackageInfoMethod.setAccessible(true);
                loadedApk = getPackageInfoMethod.invoke(activityThread, guestAi, null);
            }

            if (loadedApk == null) {
                Logger.e(TAG, "Failed to generate guest LoadedApk via GetPackageInfoNoCheck");
                return;
            }

            if (loadedApk != null) {
                // 3. Inject guest components into this LoadedApk
                Field mClassLoaderField = loadedApk.getClass().getDeclaredField("mClassLoader");
                mClassLoaderField.setAccessible(true);
                mClassLoaderField.set(loadedApk, appClassLoader);
                
                Field mApplicationInfoField = loadedApk.getClass().getDeclaredField("mApplicationInfo");
                mApplicationInfoField.setAccessible(true);
                mApplicationInfoField.set(loadedApk, guestAi);

                // Fix library and data paths for native code loading and file system access
                try {
                    String[] fields = {"mLibDir", "mBaseLibDir", "mAppDir", "mResDir", "mDataDir", "mPrimaryCpuAbi", "mClassLoader", "mResources"};
                    for (String field : fields) {
                        try {
                            Field f = loadedApk.getClass().getDeclaredField(field);
                            f.setAccessible(true);
                            if (field.contains("Lib")) {
                                f.set(loadedApk, guestAi.nativeLibraryDir);
                            } else if (field.equals("mDataDir")) {
                                f.set(loadedApk, guestAi.dataDir);
                            } else if (field.equals("mPrimaryCpuAbi")) {
                                try {
                                    Field primaryCpuAbiField = ApplicationInfo.class.getDeclaredField("primaryCpuAbi");
                                    primaryCpuAbiField.setAccessible(true);
                                    f.set(loadedApk, primaryCpuAbiField.get(guestAi));
                                } catch (Exception e) {
                                    // Fallback if field totally missing
                                }
                            } else if (field.equals("mAppDir") || field.equals("mResDir")) {
                                f.set(loadedApk, guestAi.sourceDir);
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}

                try {
                    Field mResField = loadedApk.getClass().getDeclaredField("mResources");
                    mResField.setAccessible(true);
                    mResField.set(loadedApk, CloneManager.getInstance().getResources());
                    
                    try {
                        Field mBaseClField = loadedApk.getClass().getDeclaredField("mBaseClassLoader");
                        mBaseClField.setAccessible(true);
                        mBaseClField.set(loadedApk, appClassLoader);
                    } catch (Exception ignored) {}
                } catch (Exception ignored) {}

                // 4. Register in mPackages map
                mPackages.put(packageName, new WeakReference<>(loadedApk));
                
                try {
                   Field mResourcePackagesField = activityThread.getClass().getDeclaredField("mResourcePackages");
                   mResourcePackagesField.setAccessible(true);
                   Map<String, WeakReference<?>> mResourcePackages = (Map<String, WeakReference<?>>) mResourcePackagesField.get(activityThread);
                   mResourcePackages.put(packageName, new WeakReference<>(loadedApk));
                } catch (Exception ignored) {}

                Logger.i(TAG, "GUEST LoadedApk FULLY INJECTED for: " + packageName);
            }

        } catch (Throwable e) {
            Logger.e(TAG, "LoadedApk Hook FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Object getActivityThread() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method current = atClass.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            return current.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }
}
