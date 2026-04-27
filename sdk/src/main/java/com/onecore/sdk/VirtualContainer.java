package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import dalvik.system.DexClassLoader;
import android.content.res.AssetManager;
import android.content.res.Resources;
import java.lang.reflect.Method;

import com.onecore.sdk.core.SafeExecutionManager;
import com.onecore.sdk.core.OneCoreProcessManager;
import com.onecore.sdk.utils.ReflectionHelper;

/**
 * Minimal Working Virtual Container Engine (Phase 2).
 * Handles APK Installation, Resources Injection, and Hooking.
 */
public class VirtualContainer {
    private static final String TAG = "VirtualContainer";
    private static VirtualContainer sInstance;
    
    private String mApkPath;
    private DexClassLoader mClassLoader;
    private String mPackageName;
    private Resources mResources;
    private android.app.Application mTargetApplication;
    private android.content.pm.ApplicationInfo mAppInfo;
    private java.util.Map<String, Integer> mActivityThemes = new java.util.HashMap<>();

    private VirtualContainer() {}

    public static synchronized VirtualContainer getInstance() {
        if (sInstance == null) {
            sInstance = new VirtualContainer();
        }
        return sInstance;
    }

    /**
     * Initializes the environment for a specific APK.
     */
    public boolean installApk(Context context, String apkPath, String packageName) {
        Log.i(TAG, "Installing APK for virtualization: " + apkPath);
        
        File apkFile = new File(apkPath);
        if (!apkFile.exists()) {
            Log.e(TAG, "Target APK not found at: " + apkPath);
            return false;
        }

        this.mApkPath = apkPath;
        this.mPackageName = packageName;

        try {
            android.content.pm.PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, 
                android.content.pm.PackageManager.GET_ACTIVITIES | android.content.pm.PackageManager.GET_PROVIDERS | android.content.pm.PackageManager.GET_RECEIVERS);
            if (packageInfo != null && packageInfo.applicationInfo != null) {
                mAppInfo = packageInfo.applicationInfo;
                mAppInfo.sourceDir = apkPath;
                mAppInfo.publicSourceDir = apkPath;
                mAppInfo.packageName = packageName;
                
                // Ensure native lib dir is set correctly
                File libDir = context.getDir("v_lib_" + packageName, Context.MODE_PRIVATE);
                mAppInfo.nativeLibraryDir = libDir.getAbsolutePath();
                
                Log.i(TAG, "OneCore-Init: Package Info Parsed -> " + packageName);
                Log.i(TAG, "OneCore-Init: Native Library Dir -> " + mAppInfo.nativeLibraryDir);
                
                // Register in VPM
                com.onecore.sdk.core.OneCorePackageManagerProxy.registerPackage(packageInfo);
                
                if (packageInfo.activities != null) {
                    for (android.content.pm.ActivityInfo info : packageInfo.activities) {
                        mActivityThemes.put(info.name, info.theme != 0 ? info.theme : packageInfo.applicationInfo.theme);
                    }
                }
            } else {
                Log.e(TAG, "Failed to parse APK: " + apkPath);
                return false;
            }
            
            // 0. Hidden API Bypass
            com.onecore.sdk.core.OneCoreHiddenApiFixer.bypass();

            File dexOptDir = context.getDir("v_opt_" + packageName, Context.MODE_PRIVATE);
            if (!dexOptDir.exists()) dexOptDir.mkdirs();

            // 1. Extract native libs
            String libPath = com.onecore.sdk.core.OneCoreNativeLoader.copyNativeBinaries(context, apkPath, packageName);

            // 2. Setup ClassLoader
            Log.i(TAG, "OneCore-Init: Creating DexClassLoader for target APK...");
            android.util.Log.d(TAG, ">>> STEP 1: ClassLoader Ready <<<");
            mClassLoader = new DexClassLoader(
                apkPath,
                dexOptDir.getAbsolutePath(),
                libPath,
                context.getClassLoader()
            );
            
            // Set as active context ClassLoader for this thread
            Thread.currentThread().setContextClassLoader(mClassLoader);
            Log.i(TAG, "OneCore-Init: ClassLoader created and set as ContextClassLoader.");
            
            // 2.1 Fix OBB for Games
            fixGameObb(packageName);
            
            // 2.2 Setup Resources for the target APK
            setupResources(context, apkPath);
            
            // 3. Inject LoadedApk into ActivityThread
            Log.i(TAG, "OneCore-Init: Injecting LoadedApk into ActivityThread...");
            com.onecore.sdk.core.OneCoreLoadedApkManager.getLoadedApk(context, apkPath, packageName, mClassLoader, mResources);
            
            // 4. Register Receivers
            com.onecore.sdk.core.OneCoreBroadcastManager.registerReceivers(context, packageInfo);
            
            // 5. Install Content Providers
            com.onecore.sdk.core.OneCoreContentProviderManager.installProviders(context, packageInfo.providers);
            
            Log.i(TAG, "OneCore-DEBUG: Virtual Space Ready for " + packageName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize environment", e);
            return false;
        }
    }

    private void setupResources(Context context, String apkPath) {
        try {
            // More robust resource loading
            mResources = context.getPackageManager().getResourcesForApplication(mAppInfo);
            Log.i(TAG, "Resources injected via PackageManager for: " + mPackageName);
        } catch (Exception e) {
            Log.e(TAG, "PackageManager Resources failed, falling back to manual AssetManager", e);
            try {
                AssetManager assetManager = (AssetManager) AssetManager.class.newInstance();
                Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
                addAssetPath.invoke(assetManager, apkPath);
                
                Resources hostRes = context.getResources();
                mResources = new Resources(assetManager, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
                Log.i(TAG, "Manual Resources fallback successful.");
            } catch (Exception e2) {
                Log.e(TAG, "Manual Resources fallback FAILED", e2);
            }
        }
    }

    /**
     * Launches the target activity via system intent redirected through stubs.
     */
    public boolean launch(Context context, String targetActivity) {
        if (mClassLoader == null) {
            Log.e(TAG, "Cannot launch: ClassLoader not initialized. Call installApk first.");
            return false;
        }

        Log.i(TAG, ">>> V_CORE: Launching Virtual Activity: " + targetActivity);
        
        try {
            // Check if we already have a StubActivity to host this
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(mPackageName, targetActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            Log.d(TAG, "Dispatching guest intent via context. Instrumentation will handle stub redirection...");
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Virtual Launch Dispatch Failed.", e);
            return false;
        }
    }

    /**
     * Loads and initializes the target application instance using the real Android lifecycle.
     */
    public void bindApplication(Context context, String applicationClassName, String packageName) {
        if (mClassLoader == null || mTargetApplication != null) return;
        
        Log.i(TAG, ">>> V_CORE: Binding Target Application [" + applicationClassName + "] <<<");
        Log.d(TAG, "OneCore-Bind: Package Name: " + packageName);
        Log.d(TAG, "OneCore-Bind: App Class: " + applicationClassName);
        Log.d(TAG, "OneCore-Bind: ClassLoader: " + mClassLoader);
        
        SafeExecutionManager.run("bindApplication", () -> {
            try {
                // 1. Get ActivityThread
                Object activityThread = ReflectionHelper.invokeMethod(null, "currentActivityThread");
                Log.d(TAG, "OneCore-Bind: Current ActivityThread: " + activityThread);
                
                // 2. Install Content Providers BEFORE Application.onCreate
                android.content.pm.PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(mApkPath, android.content.pm.PackageManager.GET_PROVIDERS);
                if (packageInfo != null && packageInfo.providers != null) {
                    Log.d(TAG, "OneCore-Bind: Installing " + packageInfo.providers.length + " ContentProviders...");
                    com.onecore.sdk.core.OneCoreContentProviderManager.installProviders(context, packageInfo.providers);
                }
                
                // 3. Create Application instance via LoadedApk to ensure proper integration
                Object loadedApk = com.onecore.sdk.core.OneCoreLoadedApkManager.getLoadedApk(context, mApkPath, packageName, mClassLoader, mResources);
                if (loadedApk != null) {
                    Log.i(TAG, "OneCore-Bind: LoadedApk retrieved. Calling makeApplication...");
                    // This triggers attachBaseContext and onCreate via ActivityThread logic
                    mTargetApplication = (android.app.Application) ReflectionHelper.invokeMethod(loadedApk, "makeApplication", false, null);
                    
                    if (mTargetApplication != null) {
                        Log.i(TAG, "OneCore-Bind: Application Instance Created -> " + mTargetApplication.getClass().getName());
                        android.util.Log.d(TAG, ">>> STEP 2: Application Bound <<<");
                        
                        // Spoof process name for anti-detection and consistency
                        OneCoreProcessManager.spoofProcessName(packageName);

                        // Sync with ActivityThread's initial application field so AppGlobals returns virtual app
                        ReflectionHelper.setFieldValue(activityThread, mTargetApplication, "mInitialApplication");
                        
                        // Also sync active applications list
                        java.util.List list = (java.util.List) ReflectionHelper.getFieldValue(activityThread, "mAllApplications");
                        if (list != null && !list.contains(mTargetApplication)) {
                            list.add(mTargetApplication);
                        }
                        
                        Log.i(TAG, ">>> V_CORE: Application Lifecycle SYNCED correctly. mTargetApplication=" + mTargetApplication);
                    } else {
                        Log.e(TAG, "OneCore-Bind: makeApplication returned NULL.");
                    }
                } else {
                    Log.e(TAG, "OneCore-Bind: LoadedApk is NULL, cannot bind application.");
                }
            } catch (Exception e) {
                Log.e(TAG, "!!! V_CORE: bindApplication FAILED !!!", e);
                // Last resort manual bind if LoadedApk fails
                manualBindLegacy(context, applicationClassName, packageName);
            }
        });
    }

    private void manualBindLegacy(Context context, String applicationClassName, String packageName) {
        try {
            com.onecore.sdk.core.OneCoreContextFixer.fixContext(context, packageName);
            Class<?> appClass = mClassLoader.loadClass(applicationClassName);
            mTargetApplication = (android.app.Application) appClass.newInstance();
            Method attach = android.app.Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            attach.invoke(mTargetApplication, context);
            mTargetApplication.onCreate();
        } catch (Exception e) {
            Log.e(TAG, "Legacy bind also failed", e);
        }
    }

    public android.app.Application getTargetApplication() {
        return mTargetApplication;
    }

    public String getApkPath() {
        return mApkPath;
    }

    public Integer getTheme(String activityClassName) {
        return mActivityThemes.get(activityClassName);
    }

    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    public Resources getResources() {
        return mResources;
    }

    public android.content.pm.ApplicationInfo getAppInfo() {
        return mAppInfo;
    }

    public String getPackageName() {
        return mPackageName;
    }

    private void fixGameObb(String packageName) {
        try {
            // Check multiple potential OBB locations
            java.util.List<File> potentialPaths = new java.util.ArrayList<>();
            potentialPaths.add(new File(android.os.Environment.getExternalStorageDirectory(), "Android/obb/" + packageName));
            potentialPaths.add(new File("/sdcard/Android/obb/" + packageName));
            potentialPaths.add(new File("/storage/emulated/0/Android/obb/" + packageName));
            
            boolean found = false;
            for (File path : potentialPaths) {
                if (path.exists() && path.isDirectory()) {
                    found = true;
                    Log.i(TAG, "OneCore-DEBUG: OBB directory found at: " + path.getAbsolutePath());
                    // Redirect VFS to this specific path
                    com.onecore.sdk.core.OneCoreVFS.init(packageName, getContextDataPath(packageName));
                    break;
                }
            }
            
            if (!found) {
                Log.w(TAG, "OneCore-DEBUG: OBB directory missing. BGMI/PUBG might not start. Please ensure OBB is in /sdcard/Android/obb/" + packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "OBB Check Failed", e);
        }
    }

    private String getContextDataPath(String packageName) {
        // Safe way to get virtual data path
        return "/data/data/" + com.onecore.sdk.OneCoreSDK.getContext().getPackageName() + "/app_v_data_" + packageName;
    }
}
