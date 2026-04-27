package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.Surface;
import com.onecore.sdk.VirtualDisplayManager;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import android.app.ActivityOptions;
import android.os.Build;
import android.graphics.Color;
import android.util.DisplayMetrics;

/**
 * Android 14 Sandbox StubActivity.
 * Acts as the entry point for guest applications within the virtual display.
 * Provides the physical surface for VirtualDisplay rendering.
 */
public class StubActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "OneCore-Stub";
    private SurfaceView surfaceView;

    private String pendingPkg;
    private String pendingActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Set full screen for game experience
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 2. Create SurfaceView to capture game output
        surfaceView = new SurfaceView(this);
        surfaceView.setBackgroundColor(Color.BLACK);
        surfaceView.getHolder().addCallback(this);
        setContentView(surfaceView);

        // 3. Resolve target - DEFER launch until surfaceCreated
        pendingPkg = getIntent().getStringExtra("target_package");
        pendingActivity = getIntent().getStringExtra("target_activity");
        
        if (pendingPkg == null) {
            Logger.w(TAG, "Redirection failed: StubActivity.onCreate reached with no targets!");
        } else {
            Logger.i(TAG, "StubActivity created. Waiting for Surface to launch " + pendingPkg);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.i(TAG, "StubActivity: onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Logger.i(TAG, "StubActivity: onPause");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Logger.i(TAG, "StubActivity: onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.i(TAG, "StubActivity: onStop");
    }

    private void launchTargetApp(String pkg, String activity) {
        Logger.i(TAG, "Stub handoff: Preparing to launch " + pkg + " on default display.");
        
        try {
            // Resolve main launcher activity using PackageManager (Do NOT hardcode)
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
            
            if (launchIntent == null) {
                Logger.e(TAG, "Launch intent is null for " + pkg + ". Attemping manual resolve.");
                if (activity != null) {
                    launchIntent = new Intent();
                    launchIntent.setClassName(pkg, activity);
                } else {
                    return;
                }
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            
            // Task 1: Force app rendering to VirtualDisplay
            int displayId = VirtualDisplayManager.getInstance(this).getDisplayId();
            Logger.i(TAG, "Redirecting launch to Display ID: " + displayId);
            
            ActivityOptions options = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= 26) {
                options.setLaunchDisplayId(displayId);
            }

            // Forward extras
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                launchIntent.putExtras(extras);
            }
            
            Logger.i(TAG, "Launching target activity on VirtualDisplay...");
            startActivity(launchIntent, options.toBundle());
            Logger.i(TAG, "startActivity with options called successfully.");
        } catch (Exception e) {
            Logger.e(TAG, "Launch failed", e);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        Logger.i(TAG, "Host Surface CREATED: " + surface + " | valid=" + (surface != null && surface.isValid()));
        
        // Task 2: Hook and redirect rendering surface (Native)
        com.onecore.sdk.NativeHookManager.setTargetSurface(surface);
        
        // Ensure VirtualDisplay is CREATED and ACTIVE for this surface
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        
        VirtualDisplayManager vdm = VirtualDisplayManager.getInstance(this);
        int currentId = vdm.getDisplayId();
        
        if (currentId == android.view.Display.DEFAULT_DISPLAY) {
            Logger.i(TAG, "Creating primary VirtualDisplay for guest rendering...");
            vdm.createSecureDisplay(this, "OneCore-Virtual", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, surface);
            currentId = vdm.getDisplayId();
        } else {
            Logger.i(TAG, "Reusing existing VirtualDisplay (ID: " + currentId + ")");
            vdm.syncSurface(surface);
        }

        if (currentId != android.view.Display.DEFAULT_DISPLAY) {
            Logger.i(TAG, "FINAL OUTPUT DISPLAY: VIRTUAL (ID: " + currentId + ")");
        } else {
            Logger.e(TAG, "FINAL OUTPUT DISPLAY: PHYSICAL (VirtualDisplay failed to activate)");
            // Mirroring disabled temporarily for diagnostics
            // vdm.mirrorPhysicalDisplay(surface);
        }

        // Launch deferred app
        if (pendingPkg != null) {
            String pkg = pendingPkg;
            String act = pendingActivity;
            pendingPkg = null; // Ensure only launched once
            pendingActivity = null;
            launchTargetApp(pkg, act);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Surface surface = holder.getSurface();
        Logger.i(TAG, "Host Surface CHANGED: " + width + "x" + height + " | format=" + format + " | valid=" + (surface != null && surface.isValid()));
        VirtualDisplayManager.getInstance(this).syncSurface(surface);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Logger.w(TAG, "Host Surface DESTROYED. Rendering will stop.");
        com.onecore.sdk.NativeHookManager.setTargetSurface(null);
        VirtualDisplayManager.getInstance(this).syncSurface(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VirtualDisplayManager.getInstance(this).release();
    }
}
