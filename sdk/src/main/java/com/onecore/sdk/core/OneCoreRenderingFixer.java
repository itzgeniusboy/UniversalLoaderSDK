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
            
            // 3. Force rendering to be "Opaque" for games
            window.getDecorView().setBackgroundColor(0xFF000000); 
            
            // 3.1 Force screen to stay on and window to be focused
            activity.setFinishOnTouchOutside(false);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

            // 4. Set a listener for dynamic SurfaceView additions (UE4 is lazy)
            window.getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                View decor = window.getDecorView();
                if (decor instanceof ViewGroup) {
                    fixSurfaceViews((ViewGroup) decor);
                }
            });

            // Initial run
            View decor = window.getDecorView();
            if (decor instanceof ViewGroup) {
                fixSurfaceViews((ViewGroup) decor);
            }
            
            Log.i(TAG, "OneCore-DEBUG: Rendering Pipeline fully optimized and monitoring for dynamic content.");
        });
    }

    private static void fixSurfaceViews(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof SurfaceView) {
                SurfaceView sv = (SurfaceView) child;
                // Force SurfaceView to be correctly layered
                sv.setZOrderOnTop(false); // Game should be behind UI but visible
                sv.setZOrderMediaOverlay(true);
                
                // Trigger an invalidate to wake up EGL context
                sv.invalidate();
                Log.d(TAG, "Optimized SurfaceView detected and stabilized: " + sv.toString());
            } else if (child instanceof ViewGroup) {
                fixSurfaceViews((ViewGroup) child);
            }
        }
    }
}
