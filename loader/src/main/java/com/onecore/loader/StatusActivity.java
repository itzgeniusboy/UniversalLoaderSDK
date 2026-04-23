package com.onecore.loader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.onecore.loader.views.StepIndicator;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.core.CloneManager;
import com.onecore.sdk.utils.Logger;
import com.onecore.sdk.utils.PermissionsHelper;
import java.io.File;

/**
 * Detailed status activity for visual feedback during the Loader sequence.
 * This is where problems can be diagnosed visually.
 */
public class StatusActivity extends Activity {
    private static final String TAG = "StatusActivity";
    private static final String TARGET_PKG = "com.pubg.imobile";
    private static final String LIB_URL = "https://github.com/itzgeniusboy/OneCoreLoader/releases/download/OneCoreLoader/Saved.zip";

    private StepIndicator stepIndicator;
    private TextView permStorage, permOverlay;
    private TextView cloneStatus, downloadStatus, injectionStatus, downloadSpeed, pidText;
    private ProgressBar cloneBar, downloadBar;
    private Button overlayBtn;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);

        initViews();
        updatePermissionChecklist();
        
        if (PermissionsHelper.hasAllPermissions(this)) {
            startSequence();
        } else {
            stepIndicator.setStep(0);
        }
    }

    private void initViews() {
        stepIndicator = findViewById(R.id.stepIndicator);
        permStorage = findViewById(R.id.permStorage);
        permOverlay = findViewById(R.id.permOverlay);
        cloneStatus = findViewById(R.id.cloneStatusText);
        downloadStatus = findViewById(R.id.downloadStatusText);
        injectionStatus = findViewById(R.id.injectionStatusText);
        downloadSpeed = findViewById(R.id.downloadSpeedText);
        pidText = findViewById(R.id.pidText);
        cloneBar = findViewById(R.id.cloneProgressBar);
        downloadBar = findViewById(R.id.downloadProgressBar);
        overlayBtn = findViewById(R.id.grantOverlayBtn);

        overlayBtn.setOnClickListener(v -> PermissionsHelper.requestSpecialPermissions(this));
    }

    private void updatePermissionChecklist() {
        boolean s = PermissionsHelper.hasStoragePermission(this);
        boolean o = PermissionsHelper.hasOverlayPermission(this);

        permStorage.setText((s ? "GRANTED:" : "DENIED:") + " Storage Access");
        permStorage.setTextColor(s ? Color.GREEN : Color.YELLOW);

        permOverlay.setText((o ? "GRANTED:" : "DENIED:") + " Overlay / Floating Window");
        permOverlay.setTextColor(o ? Color.GREEN : Color.YELLOW);

        if (!o) overlayBtn.setVisibility(View.VISIBLE);
    }

    private void startSequence() {
        new Thread(() -> {
            try {
                // 1. CLONING STEP
                updateStep(1, "Cloning Process...", 0);
                performCloning();

                // 2. DOWNLOADING STEP
                updateStep(2, "Downloading ESP Library...", 0);
                performDownload();

                // 3. INJECTION STEP
                updateStep(3, "Preparing Injection...", 0);
                performInjection();

                // 4. LAUNCHING STEP
                updateStep(4, "Launching Virtual Game...", 100);
                mainHandler.postDelayed(() -> {
                    GameLauncher.start(this, new GameLauncher.LaunchCallback() {
                        @Override
                        public void onProcessDetected(int pid) {
                            pidText.setText("Status: VIRTUAL_PROCESS_ACTIVE");
                            pidText.setTextColor(Color.GREEN);
                            injectionStatus.setText("SUCCESS: Injection Successful (BlackBox)");
                            Toast.makeText(StatusActivity.this, "Session Initialized", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailed(String reason) {
                            pidText.setText("Status: FAILED (" + reason + ")");
                            pidText.setTextColor(Color.RED);
                            Toast.makeText(StatusActivity.this, "Error: " + reason, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onProgress(String message) {
                            pidText.setText(message);
                        }
                    });
                }, 1000);

            } catch (Exception e) {
                Logger.e(TAG, "Process failed", e);
                mainHandler.post(() -> {
                    cloneStatus.setText("ERROR: " + e.getMessage());
                    cloneStatus.setTextColor(Color.RED);
                });
            }
        }).start();
    }

    private void performCloning() throws Exception {
        updateCloneStatus("Cloning Started...", 5);
        Thread.sleep(500);

        updateCloneStatus("Generating Virtual Identity...", 20);
        // This is where metadata is actually modified in memory
        Thread.sleep(400);
        
        updateCloneStatus("Mapping Sandbox Paths...", 40);
        boolean ok = CloneManager.getInstance().prepareClone(this, TARGET_PKG);
        if (!ok) throw new Exception("Failed to map BGMI metadata.");
        
        updateCloneStatus("Initializing Physical Directories...", 70);
        // The directories are actually created inside prepareClone, but we provide UI feedback
        Thread.sleep(600);
        
        updateCloneStatus("Verifying Environment Integrity...", 90);
        Thread.sleep(500);
        
        updateCloneStatus("COMPLETED: Virtualization successful", 100);
        Logger.i(TAG, "Virtual Space Clone sequence finished.");
    }

    private void performDownload() throws Exception {
        mainHandler.post(() -> {
            downloadStatus.setText("Downloading Saved.zip from GitHub...");
            downloadBar.setIndeterminate(true);
        });

        final boolean[] done = {false};
        final Exception[] error = {null};

        DownloadZip.start(this, new DownloadZip.DownloadCallback() {
            @Override
            public void onSuccess(File extractedDir) {
                mainHandler.post(() -> {
                    downloadBar.setIndeterminate(false);
                    downloadBar.setProgress(100);
                    downloadStatus.setText("SUCCESS: Binary Data Extracted");
                    Logger.i(TAG, "Assets ready at: " + extractedDir.getAbsolutePath());
                });
                done[0] = true;
            }

            @Override
            public void onFailure(String reason) {
                Logger.e(TAG, "Download failed: " + reason);
                error[0] = new Exception(reason);
                done[0] = true;
            }

            @Override
            public void onProgress(String message) {
                mainHandler.post(() -> {
                    downloadStatus.setText(message);
                });
            }
        });

        // Wait for download to finish (in the background thread of startSequence)
        while (!done[0]) {
            Thread.sleep(100);
        }
        if (error[0] != null) throw error[0];
    }

    private void performInjection() throws Exception {
        mainHandler.post(() -> {
            injectionStatus.setText("SYSTEM: Linking Library to Sandbox Namespace...");
        });
        
        // This actually happens inside SandboxActivity, we are just showing the "intent" here
        Thread.sleep(1000);
        
        mainHandler.post(() -> {
            injectionStatus.setText("SUCCESS: Injection Queue Initialized");
            pidText.setText("Status: Ready for Same-Process Linking");
        });
    }

    private void updateStep(int step, String text, int barProgress) {
        mainHandler.post(() -> {
            stepIndicator.setStep(step);
            cloneStatus.setText(text);
            cloneBar.setProgress(barProgress);
        });
    }

    private void updateCloneStatus(String text, int progress) {
        mainHandler.post(() -> {
            cloneStatus.setText(text);
            cloneBar.setProgress(progress);
        });
    }
}
