package com.onecore.sdk;

import android.content.Context;
import com.onecore.sdk.utils.CrashHandler;
import com.onecore.sdk.utils.Logger;

/**
 * Main entry point for the OneCore SDK Engine.
 * Handles initialization and global configurations.
 */
public class OneCoreSDK {
    private static final String TAG = "OneCoreSDK";
    private static boolean isInitialized = false;
    private static Context appContext;

    static {
        try {
            System.loadLibrary("onecore_native");
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e(TAG, "Native library onecore_native failed to load", e);
        }
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Initializes the SDK with the provided context and customer key.
     * @param context The application context.
     * @param customerKey The customer's license key.
     */
    public static void init(Context context, String customerKey) {
        if (isInitialized) return;
        
        try {
            appContext = context.getApplicationContext();
            
            // Set up Global Crash Protector
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                Logger.e("OneCoreSDK", "Global Crash Intercepted: " + throwable.getMessage(), throwable);
                // Analytics track crash if possible
                try { Analytics.getInstance().trackCrash(throwable); } catch (Exception ignored) {}
                
                // Allow some time for reporting
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                
                // Finish process to prevent inconsistent state
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            });

            // Initialize sub-components synchronously for production stability
            Logger.init(true);
            SecurityManager.init(appContext);
            CrashHandler.getInstance().init(appContext);
            
            try {
                SDKLicense.getInstance().init(appContext, customerKey);
                if (!SDKLicense.getInstance().isLicensed()) {
                    Logger.w(TAG, "License invalid, continuing in restricted mode.");
                }
            } catch (Exception e) {
                Logger.e(TAG, "License system failure: " + e.getMessage());
            }
            
            Logger.d(TAG, "SDK Shell and sub-components Initialized.");
            isInitialized = true;
        } catch (Exception e) {
            Logger.e(TAG, "SDK Initialization failed", e);
        }
    }

    public interface InstallCallback {
        void onProgress(int progress, String message);
        void onSuccess();
        void onFailure(String reason);
    }

    /**
     * Installs the hook engine and spoofer with a callback.
     */
    public static void install(InstallCallback callback) {
        if (!isInitialized) {
            if (callback != null) callback.onFailure("SDK not initialized.");
            return;
        }
        
        if (!SDKLicense.getInstance().isLicensed()) {
            SDKLicense.getInstance().showExpiryDialog();
            if (callback != null) callback.onFailure("License invalid or expired.");
            return;
        }
        
        new Thread(() -> {
            try {
                if (callback != null) callback.onProgress(10, "Mounting Filesystem...");
                Thread.sleep(500);

                Logger.d(TAG, "Installing SDK components...");
                DeviceSpoofer.getInstance().init(appContext);
                
                if (callback != null) callback.onProgress(40, "Initializing Spoofer...");
                Thread.sleep(500);

                HookEngine.getInstance().init();
                
                if (callback != null) callback.onProgress(70, "Applying Kernel Hooks...");
                Thread.sleep(800);

                // Initialize Advanced Stealth Features
                AntiFingerprint.getInstance().preventTracking();
                NetworkCapture.getInstance().startCapture();
                
                // Android 17-18 Future Proofing
                IORedirector.setupFusePassthrough(appContext);
                BinderHookManager.optimizeTransactionSize();
                
                // Load Sandbox Config from Assets
                loadSandboxConfig();
                
                // Critical: Specialized BGMI Hooks
                BGMIHooks.initHooks();
                
                if (callback != null) callback.onProgress(100, "Installation Complete");
                Thread.sleep(300);

                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Logger.e(TAG, "Installation failed", e);
                if (callback != null) callback.onFailure(e.getMessage());
            }
        }).start();
    }

    /**
     * Legacy install method for compatibility.
     */
    public static void install() {
        install(null);
    }

    // Advanced Stealth Methods
    public static void enablePrivacyShield() {
        if (!SDKLicense.getInstance().isLicensed()) return;
        AntiFingerprint.getInstance().preventTracking();
    }

    public static void startNetworkCapture() {
        if (!SDKLicense.getInstance().isLicensed()) return;
        NetworkCapture.getInstance().startCapture();
    }

    /**
     * Launches an app within the virtual container.
     * @param packageName The package name of the app to launch.
     * @param callback Confirmation callback.
     */
    public static void launchApp(String packageName, VirtualContainer.LaunchCallback callback) {
        if (!isInitialized) {
            throw new IllegalStateException("SDK must be initialized before launching apps.");
        }

        if (!SDKLicense.getInstance().isLicensed()) {
            SDKLicense.getInstance().showExpiryDialog();
            return;
        }
        
        Logger.d(TAG, "Launching app: " + packageName);
        VirtualContainer.getInstance().launch(appContext, packageName, callback);
    }

    public static Context getContext() {
        return appContext;
    }

    // Feature 1 - Anti-Detection
    public static boolean isDebuggerAttached() {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        return AntiDetect.getInstance().isDebuggerAttached();
    }

    public static boolean isEmulator() {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        return AntiDetect.getInstance().isEmulator();
    }

    public static boolean isRooted() {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        return AntiDetect.getInstance().isRooted();
    }

    public static boolean isTampered() {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        return AntiDetect.getInstance().isTampered();
    }

    // Feature 2 - License System
    public static boolean isLicenseValid() {
        return SDKLicense.getInstance().isLicensed();
    }

    public static String getExpiryDate() {
        return SDKLicense.getInstance().getExpiryDate();
    }

    public static long getDaysLeft() {
        return SDKLicense.getInstance().getDaysLeft();
    }

    // Feature 3 - Auto-Updater
    public static void checkForUpdates() {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Updater.getInstance().checkForUpdates(appContext);
    }

    public static String getCurrentVersion() {
        return Updater.getInstance().getCurrentVersion();
    }

    public static void downloadUpdate(String url) {
        Updater.getInstance().downloadUpdate(appContext, url);
    }

    public static void installUpdate(String path) {
        Updater.getInstance().installUpdate(appContext, path);
    }

    // Feature 4 - Remote Config
    public static void fetchRemoteConfig() {
        if (!SDKLicense.getInstance().isLicensed()) return;
        RemoteConfig.getInstance().fetchRemoteConfig();
    }

    public static String getConfigValue(String key) {
        if (!SDKLicense.getInstance().isLicensed()) return null;
        return RemoteConfig.getInstance().getConfigValue(key);
    }

    public static boolean isFeatureEnabled(String feature) {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        return RemoteConfig.getInstance().isFeatureEnabled(feature);
    }

    public static void refreshConfig() {
        if (!SDKLicense.getInstance().isLicensed()) return;
        RemoteConfig.getInstance().refreshConfig();
    }

    // Feature 5 - Analytics
    public static void trackEvent(String eventName) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Analytics.getInstance().trackEvent(eventName);
    }

    public static void trackCrash(Throwable throwable) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Analytics.getInstance().trackCrash(throwable);
    }

    public static void setUserProperty(String key, String value) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Analytics.getInstance().setUserProperty(key, value);
    }

    public static void flushAnalytics() {
        if (!SDKLicense.getInstance().isLicensed()) return;
        Analytics.getInstance().flushAnalytics();
    }

    // Feature 6 - Social Login Helper
    public static void twitterLogin(android.app.Activity activity, String key, String secret) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        SocialLoginHelper.getInstance().loginWithTwitter(activity, key, secret);
    }

    public static void facebookLogin(android.app.Activity activity, String appId) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        SocialLoginHelper.getInstance().loginWithFacebook(activity, appId);
    }

    public static void googleLogin(android.app.Activity activity, String clientId) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        SocialLoginHelper.getInstance().loginWithGoogle(activity, clientId);
    }

    // Feature 7 - Library Injection & Download
    public static void setLibraryDownloadUrl(String url) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        // Logic to store the URL for auto-updates
        Logger.d(TAG, "Library Download URL set to: " + url);
    }

    public static void downloadAndInject(String packageName, String url, String filename) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        VirtualContainer.getInstance().downloadAndInject(appContext, packageName, url, filename);
    }

    public static void injectLocalLibrary(String packageName, String path) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        VirtualContainer.getInstance().injectLibrary(appContext, packageName, path);
    }

    public static String getInjectionStatus() {
        if (!SDKLicense.getInstance().isLicensed()) return "NOT_LICENSED";
        return "READY";
    }

    public static void patchApk(String originalApkPath, String outputPath) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        VirtualContainer.getInstance().patchApk(originalApkPath, outputPath);
    }

    public static void enableSignatureBypass(boolean enable) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        SignatureBypass.apply(enable);
    }

    private static void loadSandboxConfig() {
        try {
            Logger.i(TAG, "Loading configurations from assets...");
            
            // Try metadata.json (New priority)
            String metaJson = com.onecore.sdk.utils.IOUtils.readAssetFile(appContext, "metadata.json");
            if (metaJson != null) {
                Logger.d(TAG, "metadata.json Loaded: " + metaJson.substring(0, Math.min(metaJson.length(), 40)) + "...");
                // In a real sandbox, this would update VirtualContainer settings
            }

            // Try sandbox_config.json (Legacy compatibility)
            String sandboxJson = com.onecore.sdk.utils.IOUtils.readAssetFile(appContext, "sandbox_config.json");
            if (sandboxJson != null) {
                Logger.d(TAG, "sandbox_config.json Loaded.");
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to load configs", e);
        }
    }
}
