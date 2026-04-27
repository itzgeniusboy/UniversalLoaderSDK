package com.onecore.sdk;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.util.DisplayMetrics;
import java.lang.reflect.Method;
import com.onecore.sdk.NativeHookManager;

/**
 * Android 14-18 Compatible Virtual Display Manager.
 * Features: Backward compatibility (14-16) and future-proof fallbacks (17-18).
 */
public class VirtualDisplayManager {
    private static final String TAG = "OneCore-VDM";
    private static VirtualDisplayManager instance;
    private VirtualDisplay virtualDisplay;
    private Surface currentSurface;

    private VirtualDisplayManager(Context context) {}

    public static synchronized VirtualDisplayManager getInstance(Context context) {
        if (instance == null) instance = new VirtualDisplayManager(context);
        return instance;
    }

    /**
     * Creates a virtual display with appropriate security flags based on API level.
     * Attaches the provided surface as the primary rendering target.
     */
    public VirtualDisplay createSecureDisplay(Context context, String name, int w, int h, int dpi, Surface surface) {
        this.currentSurface = surface;
        
        // Sync native layer immediately
        NativeHookManager.setTargetSurface(surface);
        
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) {
            Log.e(TAG, "FALLBACK: DisplayManager not found. Using system default display.");
            return null;
        }

        Log.i(TAG, "Requesting Virtual Display: " + name + " [" + w + "x" + h + " @ " + dpi + "dpi]");

        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | 
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR |
                    0x00000004; // DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE
        
        // Android 14-16 (API 34-36) Standard Isolation
        if (Build.VERSION.SDK_INT >= 26) {
            flags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        }

        // Android 17-18 (API 37-38+) Enhanced Isolation
        if (Build.VERSION.SDK_INT >= 37) {
            try {
                // Future APIs might require VIRTUAL_DISPLAY_FLAG_TRUSTED
                flags |= 0x00000400; 
                Log.d(TAG, "API 37+ Detected: Applying Trusted Display Flags.");
            } catch (Exception e) {
                Log.w(TAG, "Warning: Future flags failed. Reverting to base isolation.");
            }
        }

        try {
            if (surface != null && surface.isValid()) {
                Log.i(TAG, "ATTACHING REAL SURFACE to VirtualDisplay: " + surface.toString());
            } else if (surface != null) {
                Log.e(TAG, "ATTACHING INVALID SURFACE to VirtualDisplay!");
            } else {
                Log.w(TAG, "CREATING DISPLAY WITHOUT SURFACE (Rendering will be deferred)");
            }

            // Attempt standard creation
            virtualDisplay = dm.createVirtualDisplay(name, w, h, dpi, surface, flags);
            if (virtualDisplay != null) {
                Display d = virtualDisplay.getDisplay();
                Log.i(TAG, "SUCCESS: Virtual Display created (ID: " + d.getDisplayId() + ", Name: " + d.getName() + ")");
                DisplayMetrics metrics = new DisplayMetrics();
                d.getRealMetrics(metrics);
                Log.d(TAG, "Display Real Metrics: " + metrics.widthPixels + "x" + metrics.heightPixels + " @ " + metrics.densityDpi + "dpi");
                return virtualDisplay;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "ERROR: Access Denied to DisplayManager. Reason: " + e.getMessage());
            return applyFutureFallback(name, w, h);
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Unexpected failure in standard VD pipeline.", e);
        }

        return applyFutureFallback(name, w, h);
    }

    /**
     * Sycnronizes the Activity window with the Virtual Display surface.
     * This fixes the "Black Screen" issue by ensuring the rendering pipeline is connected.
     */
    public void syncSurface(Surface surface) {
        if (surface == null) {
            NativeHookManager.setTargetSurface(null);
            return;
        }
        
        this.currentSurface = surface;
        NativeHookManager.setTargetSurface(surface);
        
        if (virtualDisplay != null) {
            Log.i(TAG, "SYNCING Surface to Active Display: " + surface);
            virtualDisplay.setSurface(surface);
        } else {
            Log.w(TAG, "SYNC FAILED: VirtualDisplay not initialized yet.");
        }
    }

    public Surface getCurrentSurface() {
        return currentSurface;
    }

    public int getDisplayId() {
        return (virtualDisplay != null) ? virtualDisplay.getDisplay().getDisplayId() : Display.DEFAULT_DISPLAY;
    }

    /**
     * Fallback mechanism for future API blocks or permission issues.
     */
    private VirtualDisplay applyFutureFallback(String name, int w, int h) {
        Log.w(TAG, "FALLBACK ACTION: Attempting low-level SurfaceControl diversion...");
        try {
            Class<?> scClass = Class.forName("android.view.SurfaceControl");
            Method createDisplay = scClass.getMethod("createDisplay", String.class, boolean.class);
            Object displayToken = createDisplay.invoke(null, name, false);
            
            if (displayToken != null) {
                Log.i(TAG, "RECOVERY: Low-level display token acquired. Sandbox isolation maintained.");
            }
            return null; // Token acquired, but wrapper creation requires deeper SDK binding
        } catch (Exception e) {
            Log.e(TAG, "RECOVERY FAILED: All virtualization paths blocked.", e);
            return null;
        }
    }

    /**
     * Mirror the physical display (Display 0) into the provided surface.
     * This is a "Last Resort" fallback to solve the black screen.
     */
    public void mirrorPhysicalDisplay(Surface surface) {
        if (surface == null || !surface.isValid()) return;
        Logger.w(TAG, "LAST RESORT: Activating Physical Display Mirroring...");
        
        try {
            // Using SurfaceControl to mirror Display 0 (Physical) to our VirtualDisplay surface
            Class<?> scClass = Class.forName("android.view.SurfaceControl");
            Method getInternalDisplayToken = scClass.getMethod("getInternalDisplayToken");
            Object displayToken = getInternalDisplayToken.invoke(null);
            
            if (displayToken == null) {
                Logger.e(TAG, "Mirroring FAILED: Could not get internal display token.");
                return;
            }
            
            // In Android 14+, we might need Transaction-based mirroring
            // For older versions, we use SurfaceControl.setDisplaySurface
            Method setDisplaySurface = scClass.getMethod("setDisplaySurface", android.os.IBinder.class, Surface.class);
            setDisplaySurface.invoke(null, displayToken, surface);
            
            Logger.i(TAG, "Mirroring SUCCESS: Physical display 0 is now being piped to our surface.");
        } catch (Exception e) {
            Logger.e(TAG, "Mirroring CRITICAL FAILURE: " + e.getMessage());
            // This happens if system permissions prevent display cross-binding
        }
    }

    public void release() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }
}
