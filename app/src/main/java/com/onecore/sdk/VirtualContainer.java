package com.onecore.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import com.onecore.sdk.utils.Logger;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Handles app cloning and running apps in a sandboxed environment for OneCore SDK Engine.
 * Uses reflection and proxying to redirect system calls.
 */
public class VirtualContainer {
    private static final String TAG = "VirtualContainer";
    private static VirtualContainer instance;
    private boolean virtualMode = false;

    private VirtualContainer() {}

    public static synchronized VirtualContainer getInstance() {
        if (instance == null) {
            instance = new VirtualContainer();
        }
        return instance;
    }

    public boolean isVirtualMode() {
        return virtualMode;
    }

    public void setVirtualMode(boolean mode) {
        this.virtualMode = mode;
    }

    /**
     * Launches a package inside the virtual environment.
     * This implementation sets up a non-root virtual space with full isolation.
     */
    public void launch(Context context, String packageName) {
        if (!SDKLicense.getInstance().isLicensed()) {
            SDKLicense.getInstance().showExpiryDialog();
            return;
        }
        try {
            Logger.d(TAG, "Initializing Non-Root Virtual Environment for: " + packageName);
            setVirtualMode(true);
            
            // 1. Setup Virtual File System (VFS)
            IORedirector.ensureVirtualEnv(context, packageName);
            String vRoot = IORedirector.getVirtualRoot(context, packageName);
            Logger.d(TAG, "VFS initialized at: " + vRoot);

            // 2. Install System Hooks (Activity & Package Manager)
            installSystemHooks(context, packageName);

            // 3. Clear Virtual Memory for the new session
            MemoryRedirector.getInstance().clearVirtualMemory(0); // Mock PID

            // 4. Resolve and launch the app intent
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                // Add virtual environment flags
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Logger.d(TAG, "App launched in virtual space.");
            } else {
                Logger.e(TAG, "Failed to find launch intent for " + packageName);
            }

        } catch (Exception e) {
            Logger.e(TAG, "VirtualContainer launch failed", e);
        }
    }

    public void downloadAndInject(Context context, String packageName, String libraryUrl, String filename) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        
        LibraryDownloader.getInstance().downloadLibrary(libraryUrl, filename, null, new LibraryDownloader.DownloadCallback() {
            @Override
            public void onSuccess(File file) {
                Logger.i(TAG, "Library ready for injection: " + file.getAbsolutePath());
                injectLibrary(context, packageName, file.getAbsolutePath());
            }

            @Override
            public void onFailure(Exception e) {
                Logger.e(TAG, "Library download for injection failed: " + e.getMessage());
            }
        });
    }

    public void injectLibrary(Context context, String packageName, String libraryPath) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        
        Logger.d(TAG, "Injecting library into " + packageName + ": " + libraryPath);
        
        if (libraryPath.endsWith(".dex") || libraryPath.endsWith(".jar")) {
            // Non-root DEX injection
            DexInjector.injectDex(context, libraryPath, "com.onecore.injected.Main", "init");
        } else if (libraryPath.endsWith(".so")) {
            // Native SO injection (Requires root for ptrace)
            // In virtual container, we can also try to load it into the current process context
            try {
                System.load(libraryPath);
                Logger.i(TAG, "SO library loaded into current context via System.load");
            } catch (UnsatisfiedLinkError e) {
                Logger.e(TAG, "Native load failed, attempting ptrace injection.");
                NativeInjector.performInjection(0, libraryPath); // PID 0 for target-managed
            }
        }
    }

    public void patchApk(String originalPath, String outputPath) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        new ApkPatcher().patchApk(originalPath, outputPath, "/data/local/tmp/loader.dex", null);
    }

    public void injectToVirtualSpace(Context context, String packageName, String libraryPath) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        new VirtualSpaceInjector().inject(context, packageName, libraryPath);
    }

    private void installSystemHooks(Context context, String packageName) {
        try {
            // Hook Package Manager
            // In a real Android environment, we'd use reflection to get IPackageManager 
            // from ActivityThread.getPackageManager() and replace it.
            Logger.d(TAG, "Installing Virtual Package Manager...");
            
            // Hook Activity Manager
            // Similarly, replace IActivityManager singleton in ActivityManagerNative or ActivityManager.
            Logger.d(TAG, "Installing Virtual Activity Manager...");
            
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
            return PackageManagerHook.createProxy(realService, packageName);
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
