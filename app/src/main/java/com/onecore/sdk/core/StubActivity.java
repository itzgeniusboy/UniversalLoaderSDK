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

        // 3. Resolve target
        String targetPkg = getIntent().getStringExtra("target_package");
        String targetActivity = getIntent().getStringExtra("target_activity");
        
        if (targetPkg != null && targetActivity != null) {
            launchTargetApp(targetPkg, targetActivity);
        } else {
            Logger.w(TAG, "Redirection failed: StubActivity.onCreate reached with no targets!");
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
            
            // Forward extras
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                launchIntent.putExtras(extras);
            }
            
            Logger.i(TAG, "Launching target activity normally...");
            startActivity(launchIntent);
            Logger.i(TAG, "startActivity called successfully.");
        } catch (Exception e) {
            Logger.e(TAG, "Launch failed", e);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        Logger.i(TAG, "Host Surface CREATED: " + surface + " | valid=" + (surface != null && surface.isValid()));
        VirtualDisplayManager.getInstance(this).syncSurface(surface);
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
        VirtualDisplayManager.getInstance(this).syncSurface(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VirtualDisplayManager.getInstance(this).release();
    }
}
