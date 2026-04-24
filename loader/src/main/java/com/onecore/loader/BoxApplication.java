package com.onecore.loader;

import android.app.Application;
import com.onecore.sdk.OneCoreSDK;
import com.onecore.sdk.utils.Logger;
import com.onecore.sdk.core.hook.ActivityThreadHandlerHook;

/**
 * Main Application class for OneCore Loader.
 * Mandatory initialization point for the Virtualization Engine.
 */
public class BoxApplication extends Application {
    private static final String TAG = "BoxApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        
        try {
            // 1. Critical Initialization (Must be before any Activity starts)
            // Using valid production license key
            OneCoreSDK.init(this, "ONECORE-PREMIUM-X782-99");
            
            // 2. Load Core Engine Hooks
            OneCoreSDK.install();
            
            // 3. Early Instrumentation Hook (User requested global hook)
            hookInstrumentation();
            ActivityThreadHandlerHook.hook();
            
            Logger.i(TAG, "SUCCESS: Virtualization Core and Hooks initialized in Application process.");
        } catch (Exception e) {
            Logger.e(TAG, "FATAL: SDK Initialization failed. Application may not function correctly.", e);
        }
    }

    private void hookInstrumentation() {
        try {
            Logger.d(TAG, "Attempting Global Instrumentation Hook...");
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method current = atClass.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);

            Object thread = current.invoke(null);

            java.lang.reflect.Field mInstrumentation = atClass.getDeclaredField("mInstrumentation");
            mInstrumentation.setAccessible(true);

            android.app.Instrumentation base = (android.app.Instrumentation) mInstrumentation.get(thread);
            
            // Avoid double hooking
            if (!(base instanceof com.onecore.sdk.core.VAInstrumentation)) {
                com.onecore.sdk.core.VAInstrumentation proxy = new com.onecore.sdk.core.VAInstrumentation(base);
                mInstrumentation.set(thread, proxy);
                Logger.i(TAG, "Global Instrumentation Hook SUCCESS.");
            } else {
                Logger.d(TAG, "Instrumentation already hooked.");
            }

        } catch (Throwable e) {
            Logger.e(TAG, "Global Instrumentation Hook FAILED: " + e.getMessage());
        }
    }
}
