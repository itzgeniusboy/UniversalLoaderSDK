package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import android.app.ActivityOptions;
import android.os.Build;

/**
 * Android 14 Sandbox StubActivity.
 * Acts as the entry point for guest applications within the virtual display.
 * Resolves the "Original game opens instead of clone" issue.
 */
public class StubActivity extends Activity {
    private static final String TAG = "OneCore-Stub";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Set full screen for game experience
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 2. Resolve target
        String targetPkg = getIntent().getStringExtra("target_package");
        String targetActivity = getIntent().getStringExtra("target_activity");
        
        if (targetPkg != null && targetActivity != null) {
            Logger.i(TAG, "Stub handoff: " + targetPkg + "/" + targetActivity);
            
            try {
                // If instrumentation didn't intercept, we force it here
                Intent intent = new Intent();
                intent.setClassName(targetPkg, targetActivity);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                
                // Copy all extras to target intent
                Bundle extras = getIntent().getExtras();
                if (extras != null) {
                    intent.putExtras(extras);
                }
                
                startActivity(intent);
            } catch (Exception e) {
                Logger.e(TAG, "Failed to launch target activity from Stub", e);
            }
        } else {
            Logger.w(TAG, "Redirection failed: StubActivity.onCreate reached with no targets!");
        }
        
        // Don't stay in the backstack
        finish();
    }
}
