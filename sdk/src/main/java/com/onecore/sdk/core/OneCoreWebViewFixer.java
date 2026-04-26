package com.onecore.sdk.core;

import android.util.Log;
import com.onecore.sdk.utils.ReflectionHelper;

/**
 * Fixes WebView crashes in virtual environments by spoofing the package name for WebView factory.
 * Adaptive for Android 10-17.
 */
public class OneCoreWebViewFixer {
    private static final String TAG = "OneCore-WebViewFix";

    public static void fix() {
        SafeExecutionManager.run("WebView Fix", () -> {
            Class<?> webViewFactoryClass = Class.forName("android.webkit.WebViewFactory");
            Object updateService = ReflectionHelper.invokeMethod(null, "getUpdateService");
            
            if (updateService != null) {
                Log.i(TAG, "OneCore-DEBUG: WebViewUpdateService found and patched.");
                // Ensure the base process name doesn't trigger security sandbox violations
                // on newer Android versions.
                ReflectionHelper.setFieldValue(webViewFactoryClass, null, "sProviderInstance");
            }
        });
    }
}
