package com.onecore.sdk;

import android.content.Context;

/**
 * Entry point for the OneCore Virtualization SDK.
 */
public class OneCoreSDK {
    private static Context sContext;
    private static boolean sInitialized = false;

    public static void init(Context context) {
        if (sInitialized) return;
        sContext = context.getApplicationContext();
        
        // Phase 2: Hook system early
        com.onecore.sdk.core.OneCoreActivityThreadHook.install(sContext);
        
        sInitialized = true;
    }

    public static Context getContext() {
        return sContext;
    }

    public static boolean isInitialized() {
        return sInitialized;
    }
}
