package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.onecore.sdk.LauncherOrchestrator;
import com.onecore.sdk.NativeHookManager;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import java.io.File;

public class SandboxActivity extends Activity {
    private static final String TAG = "SandboxActivity";
    private String targetPackage;
    private String mainActivityClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.i(TAG, "--- Sandboxed Environment Activated ---");

        targetPackage = getIntent().getStringExtra("target_package");
        mainActivityClass = getIntent().getStringExtra("main_activity");

        setupLoadingUI();
        
        // Start launch logic with tiny delay to allow UI to render (avoid total black screen)
        new Handler(Looper.getMainLooper()).postDelayed(this::continueLaunch, 500);
    }

    private void setupLoadingUI() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xFF121212); // Dark iOS gray

        ProgressBar pb = new ProgressBar(this);
        layout.addView(pb);

        TextView tv = new TextView(this);
        tv.setText("PREPARING VIRTUAL SPACE...");
        tv.setTextColor(0xFFFFFFFF);
        tv.setPadding(0, 40, 0, 0);
        layout.addView(tv);

        setContentView(layout);
    }

    private void continueLaunch() {
        Logger.i(TAG, "Executing Step: Environment Synchronization");
        try {
            // 1. Basic hooks (UID, Filesystem redirection)
            String virtualRoot = getFilesDir().getAbsolutePath() + "/virtual/" + targetPackage;
            NativeHookManager.setupIsolation(this, virtualRoot, targetPackage);
            
            // 2. Resolve target intent
            Logger.d(TAG, "Identifying Guest Entry Point...");
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(targetPackage);

            if (launchIntent == null) {
                Logger.e(TAG, "Launch Resolution Failed: GetLaunchIntent returned null");
                handleLaunchFailure("Package Metadata Inaccessible");
                return;
            }

            // 3. Launch inside this process/environment
            Logger.i(TAG, "Triggering Guest Transition...");
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            // Note: Since this process has EnvironmentHooker/VAInstrumentation applied,
            // starting this intent should stay within the virtualized context.
            startActivity(launchIntent);
            
            Logger.i(TAG, "Launch Command Emitted. Sandbox standby for handover.");
            
            // Self-destruct this activity after successful handover
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Logger.i(TAG, "Sandbox Task Completed. Closing Shell.");
                finish();
            }, 3000);

        } catch (Exception e) {
            Logger.e(TAG, "Sandbox Execution Failure: " + e.getMessage());
            handleLaunchFailure(e.getMessage());
        }
    }

    private void handleLaunchFailure(String reason) {
        Logger.e(TAG, "!! VIRTUAL LAUNCH FAILED !! Reason: " + reason);
        Logger.i(TAG, "Auto-Initiating Fallback Bridge...");
        
        // Use the orchestrator to launch it directly
        LauncherOrchestrator.launchFallback(this, targetPackage);
        
        // Broadcoast failure if needed
        Intent result = new Intent(VirtualContainer.ACTION_LAUNCH_RESULT);
        result.putExtra("success", false);
        result.putExtra("error", reason);
        sendBroadcast(result);
        
        finish();
    }
}
