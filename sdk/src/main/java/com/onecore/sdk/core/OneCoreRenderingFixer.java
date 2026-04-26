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
            
            Log.i(TAG, "OneCore-DEBUG: Rendering Pipeline active and monitoring.");
            
            // Frame Spoofer: Keep Choreographer active to prevent UI stall
            android.view.Choreographer.getInstance().postFrameCallback(new android.view.Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    if (!activity.isFinishing()) {
                        android.view.Choreographer.getInstance().postFrameCallback(this);
                    }
                }
            });
        });
    }

    private static void fixSurfaceViews(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof SurfaceView) {
                SurfaceView sv = (SurfaceView) child;
                
                // Force correctly layered surface
                sv.setZOrderOnTop(false); 
                sv.setZOrderMediaOverlay(true);
                
                // UE4 Fix: Set Opaque format on Holder
                sv.getHolder().setFormat(android.graphics.PixelFormat.OPAQUE);
                
                // Wake up the surface if it's stalled
                sv.postInvalidate();
                
                Log.d(TAG, "UE4-Surface-Wakeup: SurfaceView stabilized: " + sv.toString());
            } else if (child instanceof ViewGroup) {
                fixSurfaceViews((ViewGroup) child);
            }
        }
    }
}
