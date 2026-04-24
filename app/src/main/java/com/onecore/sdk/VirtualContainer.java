package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.onecore.sdk.utils.Logger;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import com.onecore.sdk.core.CloneManager;
import com.onecore.sdk.utils.AndroidVersionCompat;

/**
 * Handles app cloning and running apps in a sandboxed environment for OneCore SDK Engine.
 * Optimized for Android 15 (Vanilla Ice Cream) and beyond.
 */
public class VirtualContainer {
    private static final String TAG = "VirtualContainer";
    private static VirtualContainer instance;
    private boolean virtualMode = false;
    private LaunchCallback pendingCallback;
    private ClassLoader guestClassLoader;
    public static final String ACTION_LAUNCH_RESULT = "com.onecore.sdk.LAUNCH_RESULT";

    public interface LaunchCallback {
        void onLaunchSuccess();
        void onLaunchFailed(String reason);
    }

    private VirtualContainer() {}

    public static synchronized VirtualContainer getInstance() {
        if (instance == null) {
            instance = new VirtualContainer();
        }
        return instance;
    }

    public ClassLoader getGuestClassLoader() {
        return guestClassLoader;
    }

    public void setGuestClassLoader(ClassLoader loader) {
        this.guestClassLoader = loader;
    }

    public PackageInfo getClonedPackage(String packageName) {
        return CloneManager.getInstance().getClonedPackage(packageName);
    }

    public boolean isVirtualMode() {
        return virtualMode;
    }

    public void setVirtualMode(boolean mode) {
        this.virtualMode = mode;
    }

    private android.content.res.Resources guestResources;

    public android.content.res.Resources getGuestResources() {
        return guestResources;
    }

    /**
     * Initializes the guest environment: metadata, filesystem, and classloader.
     */
    public boolean prepareGuestEnvironment(Context context, String packageName) {
        Logger.i(TAG, "Preparing Secure Environment for: " + packageName);
        
        try {
            // Set host context for metadata resolution later
            CloneManager.getInstance().setHostContext(context);

            // 1. Prepare Metadata and Filesystem
            boolean cloneOk = CloneManager.getInstance().prepareClone(context, packageName);
            if (!cloneOk) {
                Logger.e(TAG, "CloneManager failed to prepare environment.");
                return false;
            }

            PackageInfo info = CloneManager.getInstance().getClonedPackage(packageName);
            if (info == null || info.applicationInfo == null) {
                Logger.e(TAG, "Metadata not found after clone prep.");
                return false;
            }

            // Task 2: Initialize Isolated ClassLoader
            Logger.d(TAG, "Booting Isolated ClassLoader...");
            String dexPath = info.applicationInfo.sourceDir;
            String optimizedDir = context.getDir("dex_opt", Context.MODE_PRIVATE).getAbsolutePath();
            String libPath = info.applicationInfo.nativeLibraryDir;
            
            File dexFile = new File(dexPath);
            if (!dexFile.exists()) {
                Logger.e(TAG, "ABORT: APK Path does not exist: " + dexPath);
                return false;
            }

            this.guestClassLoader = new DexClassLoader(
                dexPath,
                optimizedDir,
                libPath,
                context.getClassLoader() 
            );
            CloneManager.getInstance().setClassLoader(this.guestClassLoader);
            com.onecore.sdk.core.LoadedApkHook.hook(packageName, this.guestClassLoader);

            // Task 6: Resource Loading - Load AssetManager with addAssetPath(apkPath)
            Logger.d(TAG, "Initializing Guest Resources...");
            try {
                android.content.res.AssetManager am = android.content.res.AssetManager.class.newInstance();
                Method addAssetPath = am.getClass().getMethod("addAssetPath", String.class);
                addAssetPath.invoke(am, dexPath);
                
                android.content.res.Resources hostRes = context.getResources();
                this.guestResources = new android.content.res.Resources(am, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
                CloneManager.getInstance().setResources(this.guestResources);
                Logger.i(TAG, "Guest Resources LOADED.");
            } catch (Exception e) {
                Logger.e(TAG, "Failed to load Guest Resources: " + e.getMessage());
                // This is a major cause of black screens
            }

            Logger.i(TAG, "Environment Sync: SUCCESS");
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Environment Prep Error: " + e.getMessage());
            return false;
        }
    }

    public boolean launch(Context context, String packageName, LaunchCallback callback) {
        this.pendingCallback = callback;
        return prepareAndLaunch(context, packageName);
    }

    /**
     * Entry point for LauncherOrchestrator. 
     * Performs pre-launch validations and environment setup.
     * @return true if pre-checks pass and sandbox is triggered.
     */
    public boolean prepareAndLaunch(Context context, String packageName) {
        Logger.i(TAG, "--- VIRTUAL PRE-CHECK START ---");
        
        if (context == null || packageName == null) {
            Logger.e(TAG, "Virtual Launch ABORTED: Null context/pkg");
            return false;
        }

        try {
            // 1. Prepare Metadata, ClassLoader and Filesystem
            boolean prepOk = prepareGuestEnvironment(context, packageName);
            if (!prepOk) {
                Logger.e(TAG, "Environment Preparation FAILED for " + packageName);
                return false;
            }

            PackageInfo info = getClonedPackage(packageName);
            
            // 2. Register Broadcast for feedback

            Logger.i(TAG, "Pre-checks successful. Booting sandbox process...");
            setVirtualMode(true);
            
            // Bypass restrictions
            bypassHiddenApiRestrictions();
            
            // Ensure IO redirection
            IORedirector.ensureVirtualEnv(context, packageName);
            
            // Install Core Hooks
            installSystemHooks(context, packageName);
            
            // Start Sandbox Activity
            launchInVirtualSandbox(context, packageName);
            
            return true;

        } catch (Exception e) {
            Logger.e(TAG, "Virtual Pre-check CRASH: " + e.getMessage());
            return false;
        }
    }

    private void registerLaunchResultReceiver(Context context) {
        IntentFilter filter = new IntentFilter(ACTION_LAUNCH_RESULT);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra("success", false);
                String error = intent.getStringExtra("error");
                Logger.i(TAG, "Sandbox Status Update: Success=" + success + (error != null ? " Error=" + error : ""));
                try { context.unregisterReceiver(this); } catch (Exception ignored) {}
            }
        };

        if (Build.VERSION.SDK_INT >= 34) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    private void launchInVirtualSandbox(Context context, String packageName) {
        Logger.i(TAG, "Executing Same-Process Sandbox Host for: " + packageName);
        
        PackageInfo info = CloneManager.getInstance().getClonedPackage(packageName);
        
        // This launches our Sandbox Host in the SAME process as the loader
        Intent sandboxIntent = new Intent();
        sandboxIntent.setClassName(context.getPackageName(), "com.onecore.sdk.core.SandboxActivity");
        sandboxIntent.putExtra("target_package", packageName);
        
        if (info != null && info.applicationInfo != null) {
            sandboxIntent.putExtra("source_dir", info.applicationInfo.sourceDir);
            sandboxIntent.putExtra("data_dir", info.applicationInfo.dataDir);
            sandboxIntent.putExtra("native_lib_dir", info.applicationInfo.nativeLibraryDir);
            
            // Resolve Launch Activity in Main Process to avoid process-isolation metadata misses
            String launchActivity = null;
            if (info.activities != null && info.activities.length > 0) {
                for (android.content.pm.ActivityInfo ai : info.activities) {
                    if (ai.name.toLowerCase().contains("splash") || ai.name.toLowerCase().contains("launcher")) {
                        launchActivity = ai.name;
                        break;
                    }
                }
                if (launchActivity == null) launchActivity = info.activities[0].name;
            }
            if (launchActivity != null) {
                sandboxIntent.putExtra("main_activity", launchActivity);
            }
        }
        
        // Inject library via same-process queue
        if (pendingLibraryPath != null) {
            sandboxIntent.putExtra("library_path", pendingLibraryPath);
            pendingLibraryPath = null;
        }

        sandboxIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(sandboxIntent);
    }

    /*
    private void launchTraditional(Context context, String packageName) {
        ...
    }
    */

    private boolean isRooted() {
        return new File("/system/bin/su").exists() || new File("/system/xbin/su").exists();
    }

    private void bypassHiddenApiRestrictions() {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            // Using Double Reflection to bypass hidden API checks
            // Equivalent to HiddenApiBypass logic used in modern virtualization
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);

            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntimeMethod = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            Object vmRuntime = getRuntimeMethod.invoke(null);
            
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{"L"}});
            Logger.i(TAG, "Hidden API restrictions bypassed.");
        } catch (Exception e) {
            Logger.w(TAG, "Hidden API bypass failed: " + e.getMessage());
        }
    }

    /*
    private void launchViaStub(Context context, Intent realIntent, String packageName) {
        ...
    }

    public void fallbackLaunch(Context context, String packageName) {
        ...
    }
    */

    public void injectLibrary(Context context, String packageName, String libraryPath) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        
        Logger.d(TAG, "Virtual Space Injection into " + packageName + ": " + libraryPath);
        
        File libFile = new File(libraryPath);
        if (!libFile.exists()) {
            Logger.e(TAG, "Library not found: " + libraryPath);
            return;
        }

        if (libraryPath.endsWith(".dex") || libraryPath.endsWith(".jar")) {
            // Virtual process DEX injection
            DexClassLoader loader = new DexClassLoader(
                libraryPath,
                context.getDir("dex_out", Context.MODE_PRIVATE).getAbsolutePath(),
                null,
                context.getClassLoader()
            );
            Logger.i(TAG, "DEX Loaded in same process via DexClassLoader");
        } else if (libraryPath.endsWith(".so")) {
            // BlackBox-style same-process SO load
            try {
                System.load(libraryPath);
                Logger.i(TAG, "SO successfully loaded into Virtual Process Namespace.");
            } catch (UnsatisfiedLinkError e) {
                Logger.e(TAG, "Link Failure: " + e.getMessage());
            }
        }
    }

    public void patchApk(String originalPath, String outputPath) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        new ApkPatcher().patchApk(originalPath, outputPath, "/data/local/tmp/loader.dex", null);
    }

    private String pendingLibraryPath;

    public void injectToVirtualSpace(Context context, String packageName, String libraryPath) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        // Queue for sandbox injection instead of immediate load
        this.pendingLibraryPath = libraryPath;
        Logger.d(TAG, "Library queued for Sandbox Injection: " + libraryPath);
    }

    private void installSystemHooks(Context context, String packageName) {
        try {
            Logger.i(TAG, "Initiating System Hook Sequence...");
            String vPath = OneCoreSDK.getContext().getFilesDir().getAbsolutePath() + "/virtual/" + packageName;
            com.onecore.sdk.core.BinderHookManager.installHooks(context, packageName, vPath);
            
            // Isolation & Root Bypass are handled via these hooks
            Logger.d(TAG, "Root Detection Bypass active: Reporting Non-Root state.");
            
            // Allow system-level intents for social login apps
            Logger.d(TAG, "Social Login intents allowed: Twitter, Facebook, Chrome.");
        } catch (Exception e) {
            Logger.e(TAG, "Failed to install system hooks", e);
        }
    }

    /**
     * Proxies a system service with virtual environment context.
     */
    public Object proxyService(Object realService, final String serviceName, String packageName) {
        if ("package".equals(serviceName)) {
            return PackageManagerHook.createProxy(realService);
        } else if ("activity".equals(serviceName)) {
            return ActivityManagerHook.createProxy(realService);
        }
        
        return Proxy.newProxyInstance(
            realService.getClass().getClassLoader(),
            realService.getClass().getInterfaces(),
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    return method.invoke(realService, args);
                }
            }
        );
    }
}
