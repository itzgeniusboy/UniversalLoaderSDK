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
            VirtualContainer container = VirtualContainer.getInstance();
            
            // 0. Ensure Virtual Environment is ready in this process (Only call once!)
            boolean synced = container.installApk(this, container.getApkPath(), targetPackage);
            if (!synced) {
                Logger.e(TAG, "Process Environment Sync Failed!");
                handleLaunchFailure("Process Out of Sync");
                return;
            }

            ClassLoader guestLoader = container.getClassLoader();
            if (guestLoader != null) {
                Logger.d(TAG, "Patching Thread Context ClassLoader...");
                Thread.currentThread().setContextClassLoader(guestLoader);
            }

            // 1. Basic hooks (UID, Filesystem redirection)
            com.onecore.sdk.IORedirector.ensureVirtualEnv(this, targetPackage);
            
            // 1.5 Apply Deep System Hooks
            Logger.i(TAG, "Applying Anti-Root & Virtualization Hooks...");
            com.onecore.sdk.core.UidSpoofing.apply(10000 + (int)(Math.random() * 5000));
            
            // 2. Resolve target activity
            String mainActivity = getIntent().getStringExtra("main_activity");
            if (mainActivity == null) {
                mainActivity = mainActivityClass;
            }

            if (mainActivity == null) {
                Logger.e(TAG, "Launch Resolution Failed");
                handleLaunchFailure("Metadata Resolution Failed");
                return;
            }

            // 3. Hand over to system via StubActivity for virtualized execution
            Logger.i(TAG, "Launching Guest Activity via StubActivity: " + mainActivity);
            Intent intent = new Intent(this, StubActivity.class);
            intent.putExtra("target_package", targetPackage);
            intent.putExtra("target_activity", mainActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            startActivity(intent);
            
            Logger.i(TAG, "Virtual startup sequence initiated. StubActivity hosting rendering.");
            
            // Finish loader activity immediately after handoff to StubActivity
            finish();

        } catch (Exception e) {
            Logger.e(TAG, "Sandbox Execution Failure: " + e.getMessage());
            handleLaunchFailure(e.getMessage());
        }
    }

    private void handleLaunchFailure(String reason) {
        Logger.e(TAG, "!! VIRTUAL LAUNCH FAILED !! Reason: " + reason);
        
        // NO MORE FALLBACK TO REAL APP as per security requirement
        Logger.w(TAG, "System Fallback BLOCKED for security.");
        
        // Broadcast failure
        Intent result = new Intent(VirtualContainer.ACTION_LAUNCH_RESULT);
        result.putExtra("success", false);
        result.putExtra("error", reason);
        sendBroadcast(result);
        
        finish();
    }
}
