package com.onecore.sdk.core;

import android.content.Context;
import android.util.Log;
import com.onecore.sdk.core.reflex.ReflectionHelper;
import java.lang.reflect.Method;

/**
 * Entry point for dynamic, version-adaptive virtualization hooks.
 */
public class OneCoreActivityThreadHook {
    private static final String TAG = "OneCore-ATHook";

    public static void install(Context context) {
        SystemVersionManager.logVersionInfo();
        
        SafeExecutionManager.run("Main Hook Initialization", () -> {
            Log.i(TAG, ">>> INITIATING OneCore SYSTEM HOOK <<<");
            
            // 0. Bypass system restrictions
            OneCoreHiddenApiFixer.bypass();
            
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = ReflectionHelper.invokeMethod(null, "currentActivityThread");
            if (activityThread == null) {
                // Fallback attempt
                Method m = activityThreadClass.getDeclaredMethod("currentActivityThread");
                m.setAccessible(true);
                activityThread = m.invoke(null);
            }

            // 1. Hook Instrumentation
            final Object at = activityThread;
            SafeExecutionManager.run("Instrumentation Hook", () -> {
                android.app.Instrumentation baseInstrumentation = (android.app.Instrumentation) ReflectionHelper.getFieldValue(at, "mInstrumentation");
                OneCoreInstrumentation customInstrumentation = new OneCoreInstrumentation(baseInstrumentation);
                ReflectionHelper.setFieldValue(at, customInstrumentation, "mInstrumentation");
            });
            
            // 2. Hook Service Proxies with safe execution
            SafeExecutionManager.run("AMS Proxy", () -> OneCoreAMSProxy.install(context.getPackageName()));
            SafeExecutionManager.run("PMS Proxy", OneCorePackageManagerProxy::install);
            SafeExecutionManager.run("CP Proxy", OneCoreContentProviderProxy::install);
            SafeExecutionManager.run("Service Manager", () -> OneCoreServiceManager.install(context));
            
            // 3. Spoofing and optimizations
            OneCoreBuildProxy.spoof();
            OneCoreUidProxy.spoof();
            OneCoreDeviceSpoofing.install();
            OneCoreAccountManagerProxy.install();
            OneCoreGLESSpoofer.apply();
            OneCoreAntiCheatBypass.apply();
            OneCoreWebViewFixer.fix();
            OneCoreCrashHandler.install();
            OneCoreAntiDetection.apply();
            OneCoreMemoryOptimizer.optimize();
            
            Log.i(TAG, "OneCore-DEBUG: All system hooks successfully applied.");
        });
    }
}
