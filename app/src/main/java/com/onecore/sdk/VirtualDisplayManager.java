package com.onecore.sdk;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

/**
 * Manages virtual displays for the sandbox environment.
 */
public class VirtualDisplayManager {
    private static final String TAG = "VirtualDisplayManager";
    private static VirtualDisplayManager instance;
    private VirtualDisplay virtualDisplay;
    private DisplayManager displayManager;

    private VirtualDisplayManager(Context context) {
        displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    }

    public static synchronized VirtualDisplayManager getInstance(Context context) {
        if (instance == null) {
            instance = new VirtualDisplayManager(context);
        }
        return instance;
    }

    public VirtualDisplay createVirtualDisplay(String name, int width, int height, int density, Surface surface) {
        if (displayManager == null) return null;

        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

        // FIX: Add VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY to isolate content
        // This prevents the sandbox output from leaking onto the main screen
        flags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;

        try {
            virtualDisplay = displayManager.createVirtualDisplay(
                    name, width, height, density, surface, flags);
            Log.i(TAG, "Virtual display initialized for BGMI 4.3.0 Sandbox.");
            return virtualDisplay;
        } catch (Exception e) {
            Log.e(TAG, "Failure creating virtual sandbox display: " + e.getMessage());
            return null;
        }
    }

    public int getVirtualDisplayId() {
        if (virtualDisplay != null) {
            return virtualDisplay.getDisplay().getDisplayId();
        }
        return Display.DEFAULT_DISPLAY;
    }

    public void release() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }
}
