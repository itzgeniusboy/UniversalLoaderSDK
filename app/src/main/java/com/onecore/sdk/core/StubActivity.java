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
            setupVirtualDisplay(targetPkg, targetActivity);
        } else {
            Logger.w(TAG, "Redirection failed: StubActivity.onCreate reached with no targets!");
        }
    }

    private void setupVirtualDisplay(String pkg, String activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        
        // Initialize Virtual Display (Surface will be synced in surfaceCreated)
        VirtualDisplayManager.getInstance(this).createSecureDisplay(
            this, "OneCore-Virtual", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, null
        );

        Logger.i(TAG, "Stub handoff: " + pkg + "/" + activity);
        
        try {
            Intent intent = new Intent();
            intent.setClassName(pkg, activity);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Forward extras
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            
            // Critical: Launch target in the Virtual Display
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(VirtualDisplayManager.getInstance(this).getDisplayId());
                startActivity(intent, options.toBundle());
            } else {
                startActivity(intent);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to launch target activity from Stub", e);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Logger.i(TAG, "Host Surface CREATED. Syncing with Virtual Display...");
        VirtualDisplayManager.getInstance(this).syncSurface(holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Logger.d(TAG, "Host Surface CHANGED: " + width + "x" + height);
        VirtualDisplayManager.getInstance(this).syncSurface(holder.getSurface());
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
