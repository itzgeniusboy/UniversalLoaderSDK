package com.onecore.sdk.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Utility for handling Android version compatibility, including Edge-to-Edge support.
 * Optimized for Android 15 (API 35), 16, and 17.
 */
public class AndroidVersionCompat {

    @ChecksSdkIntAtLeast(api = 35)
    public static boolean isAndroid15() {
        return Build.VERSION.SDK_INT >= 35;
    }

    @ChecksSdkIntAtLeast(api = 36) // Android 16
    public static boolean isAndroid16() {
        return Build.VERSION.SDK_INT >= 36;
    }

    @ChecksSdkIntAtLeast(api = 37) // Android 17
    public static boolean isAndroid17() {
        return Build.VERSION.SDK_INT >= 37;
    }

    @ChecksSdkIntAtLeast(api = 38) // Android 18
    public static boolean isAndroid18() {
        return Build.VERSION.SDK_INT >= 38;
    }

    /**
     * Set up Edge-to-Edge display for modern Android versions.
     * Required for Android 15+.
     */
    public static void setupEdgeToEdge(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        Window window = activity.getWindow();
        
        // Android 15+ enforcement: Edge-to-edge is default but we should explicitly manage it
        if (Build.VERSION.SDK_INT >= 35) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            
            // Contrast handling for light/dark modes
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller != null) {
                // By default assume dark theme for BGMI
                controller.setAppearanceLightStatusBars(false);
                controller.setAppearanceLightNavigationBars(false);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Legacy support for Edge-to-Edge
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        // Handle cutout (notches)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(layoutParams);
        }
    }
}
