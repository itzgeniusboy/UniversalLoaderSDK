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
            String targetActivity = getIntent().getStringExtra("target_activity");
            if (targetActivity == null) {
                Logger.e(TAG, "No target activity specified for handoff.");
                finish();
                return;
            }

            Logger.d(TAG, "Attempting Direct Class Load: " + targetActivity);
            
            // Load the class from the guest class loader
            Class<?> activityClass = getClassLoader().loadClass(targetActivity);
            Intent intent = new Intent(this, activityClass);
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivity(intent);
            Logger.i(TAG, "Direct Load Success: " + targetActivity);

            // Do not finish immediately to allow task transition
            // finish(); 
            
        } catch (Exception e) {
            Logger.e(TAG, "Direct Load Failed, falling back: " + e.getMessage());
            fallbackLaunch(packageName);
        }
    }

    private void fallbackLaunch(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            finish();
        } catch (Exception ignored) {}
    }

    @Override
    public ClassLoader getClassLoader() {
        ClassLoader guestLoader = VirtualContainer.getInstance().getGuestClassLoader();
        return guestLoader != null ? guestLoader : super.getClassLoader();
    }
}
