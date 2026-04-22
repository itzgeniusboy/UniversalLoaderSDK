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
 * Android 14 Sandbox StubActivity.
 * Acts as the entry point for guest applications within the virtual display.
 * Resolves the "Original game opens instead of clone" issue.
 */
public class StubActivity extends Activity {
    private static final String TAG = "OneCore-Stub";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Setup Transparent UI
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        String targetPackage = getIntent().getStringExtra("target_package");
        int displayId = getIntent().getIntExtra("display_id", 0);
        
        if (targetPackage == null) {
            Logger.e(TAG, "No target package found for virtualization.");
            finish();
            return;
        }

        Logger.i(TAG, "Virtual Core activating for: " + targetPackage);

        // Crucial: Start the game from THIS activity context on THIS display
        launchTargetInVirtualSpace(targetPackage, displayId);
    }

    private void launchTargetInVirtualSpace(String packageName, int displayId) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) {
                Logger.e(TAG, "Failed to resolve launch intent for: " + packageName);
                finish();
                return;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (Build.VERSION.SDK_INT >= 26 && displayId > 0) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(displayId);
                // StartActivity from here ensures the task is bound to the stub's display
                startActivity(intent, options.toBundle());
                Logger.d(TAG, "Sandbox Handoff Success. DisplayID: " + displayId);
            } else {
                startActivity(intent);
            }

            // Finish the stub once handoff is successful
            finish();
            
        } catch (Exception e) {
            Logger.e(TAG, "Sandbox Handoff Failed: " + e.getMessage());
            finish();
        }
    }
}
