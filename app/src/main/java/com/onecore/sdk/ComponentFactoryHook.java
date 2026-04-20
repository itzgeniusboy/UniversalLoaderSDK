package com.onecore.sdk;

import android.app.AppComponentFactory;
import android.app.Application;
import android.content.Intent;
import com.onecore.sdk.utils.Logger;

/**
 * AppComponentFactory Hook for OneCore SDK Engine.
 * Allows early execution during app launch (API 28+).
 */
public class ComponentFactoryHook extends AppComponentFactory {
    private static final String TAG = "ComponentFactoryHook";

    @Override
    public Application instantiateApplication(ClassLoader cl, String className) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Logger.d(TAG, "AppComponentFactory: Early bridge execution started.");
        
        // This is the earliest point where we can execute code in a process
        // We can initialize the SDK hooks here before the real Application class is created.
        
        try {
            // Early Init Logic
            OneCoreSDK.install();
        } catch (Exception e) {
            Logger.e(TAG, "Early bridge execution failed", e);
        }
        
        return super.instantiateApplication(cl, className);
    }
}
