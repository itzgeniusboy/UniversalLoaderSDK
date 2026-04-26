package com.onecore.sdk.core;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/**
 * Ensures the rendering pipeline is active and visible for games.
 */
public class OneCoreRenderingFixer {
    private static final String TAG = "OneCore-Renderer";

    public static void fix(Activity activity) {
        if (activity == null) return;

        SafeExecutionManager.run("Rendering Fix", () -> {
            Window window = activity.getWindow();
            if (window == null) return;

            // 1. Ensure hardware acceleration is truly on
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            
            // 2. Clear translucent flags if they leaked from stub
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            
            // 3. Force rendering to be "Opaque" for games like BGMI
            window.getDecorView().setBackgroundColor(0xFF000000); // Black background fallback
            
            // 4. Staggered search for SurfaceViews to ensure they are on top
            View decor = window.getDecorView();
            if (decor instanceof ViewGroup) {
                fixSurfaceViews((ViewGroup) decor);
            }
            
            Log.i(TAG, "OneCore-DEBUG: Rendering Pipeline fully optimized for Gaming.");
        });
    }

    private static void fixSurfaceViews(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof SurfaceView) {
                SurfaceView sv = (SurfaceView) child;
                // Force SurfaceView to be on top of the window decor but behind any UI overlays
                sv.setZOrderMediaOverlay(true);
                Log.d(TAG, "Optimized SurfaceView found: " + sv.toString());
            } else if (child instanceof ViewGroup) {
                fixSurfaceViews((ViewGroup) child);
            }
        }
    }
}
