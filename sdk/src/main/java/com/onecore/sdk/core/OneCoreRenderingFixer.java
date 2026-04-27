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
        
        // Ensure surface is ready for EGL
        ensureSurface(activity);

        SafeExecutionManager.run("Rendering Fix", () -> {
            Window window = activity.getWindow();
            if (window == null) return;

            // 1. Ensure hardware acceleration is truly on
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            
            // 2. Clear translucent flags if they leaked from stub
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            
            // Task 7: Handle protected/secure surfaces - Prevent FLAG_SECURE from blocking capture/rendering
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            
            // 3. Force OPAQUE window for EGL performance and visibility
            window.setFormat(android.graphics.PixelFormat.OPAQUE);
            
            // 3.1 Force orientation to LANDSCAPE for games to prevent rotation glitches
            if (activity.getRequestedOrientation() != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }

            // 4. Force rendering to be "Transparent" for games to allow SurfaceView through
            window.getDecorView().setBackgroundColor(0x00000000); 
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            
            // 5. Force screen to stay on and window to be focused
            activity.setFinishOnTouchOutside(false);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

            // 4. Set a listener for dynamic SurfaceView additions (UE4 is lazy)
            window.getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    View decor = window.getDecorView();
                    if (decor instanceof ViewGroup) {
                        fixSurfaceViews((ViewGroup) decor);
                    }
                }
            });

            // Initial run
            View decor = window.getDecorView();
            if (decor instanceof ViewGroup) {
                fixSurfaceViews((ViewGroup) decor);
            } else if (decor instanceof SurfaceView) {
                fixSingleSurfaceView((SurfaceView) decor);
            }
            
            // UE4/BGMI Fix: Re-run after a small delay to catch lazy surface initialization
            decor.postDelayed(() -> {
                if (decor instanceof ViewGroup) {
                    fixSurfaceViews((ViewGroup) decor);
                } else if (decor instanceof SurfaceView) {
                    fixSingleSurfaceView((SurfaceView) decor);
                }
                
                // Black Screen Recovery: 
                // Sometimes UE4 gets stuck in a hidden state if the virtual window didn't sync.
                // We force a layout refresh.
                decor.requestLayout();
                Log.d(TAG, "UE4: Post-layout stability check completed.");
            }, 1000);
            
            Log.i(TAG, "OneCore-DEBUG: Rendering Pipeline active and monitoring.");
            
            // Frame Spoofer: Keep Choreographer active to prevent UI stall
            android.view.Choreographer.getInstance().postFrameCallback(new android.view.Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    if (!activity.isFinishing()) {
                        // Force invalidate of decor to keep compositor active
                        window.getDecorView().postInvalidateOnAnimation();
                        android.view.Choreographer.getInstance().postFrameCallback(this);
                    }
                }
            });
        });
    }

    private static void ensureSurface(Activity activity) {
        // Some game engines (UE4) fail if the surface is not immediate
        // We can force a small delay or a layout pass
        View decor = activity.getWindow().getDecorView();
        if (decor != null) {
            decor.requestLayout();
            decor.invalidate();
        }
    }

    private static void fixSurfaceViews(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof SurfaceView) {
                fixSingleSurfaceView((SurfaceView) child);
            } else if (child instanceof ViewGroup) {
                fixSurfaceViews((ViewGroup) child);
            }
        }
    }

    private static void fixSingleSurfaceView(SurfaceView sv) {
        // UE4/BGMI Fix: The game needs to be on top to be visible through the decor
        // sv.setZOrderOnTop(true); 
        // sv.setZOrderMediaOverlay(false);
        
        // UE4 Fix: Set Opaque format on Holder
        // sv.getHolder().setFormat(android.graphics.PixelFormat.OPAQUE);
        
        // Fix Surface Control for EGL
        try {
            android.view.SurfaceHolder holder = sv.getHolder();
            if (holder != null && holder.getSurface() != null) {
                 if (!holder.getSurface().isValid()) {
                    Log.w(TAG, "Surface is invalid: " + sv);
                 } else {
                    Log.i(TAG, "Surface is VALID and READY: " + holder.getSurface());
                 }
            }
        } catch (Exception ignored) {}
        
        // Disable Secure flag as it can cause black screen in some virtual environments
        try {
            sv.setSecure(false);
        } catch (Exception ignored) {}
        
        // Aggressive fix: Some devices need a brief toggle to wake up the buffer
        if (sv.getVisibility() == View.VISIBLE) {
            sv.setWillNotDraw(false);
            sv.postInvalidate();
        }
        
        // UE4 specific: Set fixed size if it's too small
        if (sv.getWidth() > 0 && sv.getWidth() < 10) {
             Log.w(TAG, "Detected suspiciously small SurfaceView (" + sv.getWidth() + "x" + sv.getHeight() + "), might be hidden UE4 layer.");
        }
        
        // Force buffer update
        if (sv.getWidth() > 0 && sv.getHeight() > 0) {
            sv.getHolder().setFixedSize(sv.getWidth(), sv.getHeight());
        }
        
        // Wake up the surface if it's stalled
        sv.postInvalidate();
        
        Log.d(TAG, "UE4-Surface-Wakeup: SurfaceView stabilized: " + sv.toString());
    }
}
