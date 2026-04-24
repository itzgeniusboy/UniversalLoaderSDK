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
            // 0. Ensure Virtual Environment is ready in this process
            boolean synced = VirtualContainer.getInstance().prepareGuestEnvironment(this, targetPackage);
            if (!synced) {
                Logger.e(TAG, "Process Environment Sync Failed!");
                handleLaunchFailure("Process Out of Sync");
                return;
            }

            ClassLoader guestLoader = VirtualContainer.getInstance().getGuestClassLoader();
            if (guestLoader != null) {
                Logger.d(TAG, "Patching Thread Context ClassLoader...");
                Thread.currentThread().setContextClassLoader(guestLoader);
            }

            // 1. Basic hooks (UID, Filesystem redirection)
            String virtualRoot = getFilesDir().getAbsolutePath() + "/virtual/" + targetPackage;
            NativeHookManager.setupIsolation(this, virtualRoot, targetPackage);
            
            // 1.5 Apply Deep System Hooks
            Logger.i(TAG, "Applying Anti-Root & Virtualization Hooks...");
            EnvironmentHooker.apply(this, targetPackage, virtualRoot);
            UidSpoofing.apply(this, 10000 + (int)(Math.random() * 5000));
            
            // 2. Resolve target activity from virtual metadata
            Logger.d(TAG, "Resolving Guest Entry Point from Virtual Metadata...");
            String mainActivity = getIntent().getStringExtra("main_activity");
            
            if (mainActivity == null) {
                // Try to find it from VirtualContainer metadata
                PackageInfo info = VirtualContainer.getInstance().getClonedPackage(targetPackage);
                if (info != null && info.activities != null && info.activities.length > 0) {
                    mainActivity = info.activities[0].name;
                    for (android.content.pm.ActivityInfo ai : info.activities) {
                        if (ai.name.toLowerCase().contains("main") || ai.name.toLowerCase().contains("splash")) {
                            mainActivity = ai.name;
                            break;
                        }
                    }
                }
            }

            if (mainActivity == null) {
                Logger.e(TAG, "Launch Resolution Failed: Could not find main activity in virtual metadata");
                handleLaunchFailure("Metadata Resolution Failed");
                return;
            }

            // 3. Hand over to StubActivity for virtualized class loading
            Logger.i(TAG, "Handing over to StubActivity for: " + mainActivity);
            Intent stubIntent = new Intent(this, StubActivity.class);
            stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            stubIntent.putExtra("target_package", targetPackage);
            stubIntent.putExtra("target_activity", mainActivity);
            
            startActivity(stubIntent);
            
            Logger.i(TAG, "Stub Handover Successful. Sandbox shell retiring.");
            
            // Self-destruct this activity after handover
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 2000);

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
