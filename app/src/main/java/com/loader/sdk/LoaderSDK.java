package com.loader.sdk;

import android.content.Context;
import com.loader.sdk.utils.CrashHandler;
import com.loader.sdk.utils.Logger;

/**
 * Main entry point for the UniversalLoaderSDK.
 * Handles initialization and global configurations.
 */
public class LoaderSDK {
    private static final String TAG = "LoaderSDK";
    private static boolean isInitialized = false;
    private static Context appContext;

    /**
     * Initializes the SDK with the provided context.
     * @param context The application context.
     */
    public static void init(Context context) {
        if (isInitialized) return;
        
        appContext = context.getApplicationContext();
        Logger.init(true); // Enable logging by default
        CrashHandler.getInstance().init(appContext);
        
        Logger.d(TAG, "SDK Initialized successfully.");
        isInitialized = true;
    }

    /**
     * Installs the hook engine and spoofer.
     */
    public static void install() {
        if (!isInitialized) {
            throw new IllegalStateException("SDK must be initialized before installation.");
        }
        
        Logger.d(TAG, "Installing SDK components...");
        DeviceSpoofer.getInstance().init(appContext);
        HookEngine.getInstance().init();
    }

    /**
     * Launches an app within the virtual container.
     * @param packageName The package name of the app to launch.
     */
    public static void launchApp(String packageName) {
        if (!isInitialized) {
            throw new IllegalStateException("SDK must be initialized before launching apps.");
        }
        
        Logger.d(TAG, "Launching app: " + packageName);
        VirtualContainer.getInstance().launch(appContext, packageName);
    }

    public static Context getContext() {
        return appContext;
    }

    // NEW ADDITION: Feature 1 - Anti-Detection
    public static boolean isDebuggerAttached() {
        return AntiDetect.getInstance().isDebuggerAttached();
    }

    public static boolean isEmulator() {
        return AntiDetect.getInstance().isEmulator();
    }

    public static boolean isRooted() {
        return AntiDetect.getInstance().isRooted();
    }

    public static boolean isTampered() {
        return AntiDetect.getInstance().isTampered();
    }

    // NEW ADDITION: Feature 2 - License System
    public static void setLicenseKey(String key) {
        LicenseManager.getInstance().setLicenseKey(key);
    }

    public static boolean isLicenseValid() {
        return LicenseManager.getInstance().isLicenseValid(appContext);
    }

    public static java.util.Date getExpiryDate() {
        return LicenseManager.getInstance().getExpiryDate();
    }

    public static void setTrialDays(int days) {
        LicenseManager.getInstance().setTrialDays(days);
    }

    // NEW ADDITION: Feature 3 - Auto-Updater
    public static void checkForUpdates() {
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

    // NEW ADDITION: Feature 4 - Remote Config
    public static void fetchRemoteConfig() {
        RemoteConfig.getInstance().fetchRemoteConfig();
    }

    public static String getConfigValue(String key) {
        return RemoteConfig.getInstance().getConfigValue(key);
    }

    public static boolean isFeatureEnabled(String feature) {
        return RemoteConfig.getInstance().isFeatureEnabled(feature);
    }

    public static void refreshConfig() {
        RemoteConfig.getInstance().refreshConfig();
    }

    // NEW ADDITION: Feature 5 - Analytics
    public static void trackEvent(String eventName) {
        Analytics.getInstance().trackEvent(eventName);
    }

    public static void trackCrash(Throwable throwable) {
        Analytics.getInstance().trackCrash(throwable);
    }

    public static void setUserProperty(String key, String value) {
        Analytics.getInstance().setUserProperty(key, value);
    }

    public static void flushAnalytics() {
        Analytics.getInstance().flushAnalytics();
    }
}
