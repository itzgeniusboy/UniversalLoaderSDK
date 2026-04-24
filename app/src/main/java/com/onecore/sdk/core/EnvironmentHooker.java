package com.onecore.sdk.core;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import com.onecore.sdk.utils.Logger;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Orchestrates the application of hooks to the current process.
 * This ensures system services are proxied BEFORE the target app starts.
 */
public class EnvironmentHooker {
    private static final String TAG = "EnvironmentHooker";

    public static void apply(Context context, String fakePackageName, String virtualPath) {
        try {
            Logger.i(TAG, "Applying Environment Hooks for " + fakePackageName);

            // 0. Initialize Virtual Storage
            VirtualStorage.init(virtualPath);

            // 1. Install Global Service & ActivityThread Hooks
             BinderHookManager.installHooks(context, fakePackageName, virtualPath);

            // 2. Patch LoadedApk specifically for the Sandbox process
            ClassLoader guestLoader = com.onecore.sdk.VirtualContainer.getInstance().getGuestClassLoader();
            if (guestLoader != null) {
                LoadedApkHook.hook(fakePackageName, guestLoader);
            }

            // 3. Install Guest Providers (Sync content providers for the guest app)
            installGuestProviders(fakePackageName);

            Logger.i(TAG, "Deep Virtualization Hooks Applied Successfully.");
        } catch (Exception e) {
            Logger.e(TAG, "Global Hooking Failure", e);
        }
    }

    private static void installGuestProviders(String packageName) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            currentAtMethod.setAccessible(true);
            Object at = currentAtMethod.invoke(null);

            Logger.i(TAG, "Installing Guest Providers for " + packageName);
            android.content.pm.PackageInfo info = com.onecore.sdk.VirtualContainer.getInstance().getClonedPackage(packageName);
            if (info == null || info.providers == null) return;

            android.app.Application app = ApplicationManager.getVirtualApp();
            if (app != null) {
                ProviderManager.installProviders(com.onecore.sdk.OneCoreSDK.getContext(), app, packageName, java.util.Arrays.asList(info.providers));
            } else {
                Logger.w(TAG, "Cannot install guest providers: Application not yet initialized.");
            }
            
            Logger.d(TAG, "Guest Providers installed: " + info.providers.length);
        } catch (Exception e) {
            Logger.e(TAG, "Provider Installation Failure", e);
        }
    }

    public static void setInitialApplication(Object activityThread, android.app.Application app) {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(activityThread, app);
            Logger.i(TAG, "mInitialApplication swapped to Guest instance.");
        } catch (Exception e) {
            Logger.w(TAG, "Failed to swap mInitialApplication: " + e.getMessage());
        }
    }
}
