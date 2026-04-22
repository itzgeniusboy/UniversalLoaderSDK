package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import com.onecore.sdk.utils.Logger;
import android.app.ActivityOptions;
import android.os.Build;

/**
 * Android 14+ Sandbox StubActivity.
 * Acts as the entry point for guest applications within the virtual display.
 */
public class StubActivity extends Activity {
    private static final String TAG = "OneCore-Stub";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Setup UI for target container
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        String targetPackage = getIntent().getStringExtra("target_package");
        
        if (targetPackage == null) {
            Logger.e(TAG, "No target package specified. Finishing stub.");
            finish();
            return;
        }

        Logger.i(TAG, "Stub initializing virtualization for: " + targetPackage);

        // 2. Launch the real game intent
        launchInSandbox(targetPackage);
    }

    private void launchInSandbox(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) {
                Logger.e(TAG, "Could not find launch intent for " + packageName);
                finish();
                return;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);

            // Android 14 Virtual Display Handoff
            if (Build.VERSION.SDK_INT >= 26) {
                int displayId = getIntent().getIntExtra("display_id", 0);
                if (displayId > 0) {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setLaunchDisplayId(displayId);
                    startActivity(intent, options.toBundle());
                    Logger.d(TAG, "Handoff to Virtual Display ID: " + displayId);
                } else {
                    startActivity(intent);
                }
            } else {
                startActivity(intent);
            }

            // Keep stub alive in background as container host or finish
            finish(); 
            
        } catch (Exception e) {
            Logger.e(TAG, "Sandbox launch fatal error", e);
            finish();
        }
    }
}
