package com.onecore.loader;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
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
    private static final String LIB_URL = "https://parallaxserver.online/filemanager/raw.php?file=libOWNERHUBEE.so";

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

        permStorage.setText((s ? "✅" : "⚠️") + " Storage Access");
        permStorage.setTextColor(s ? Color.GREEN : Color.YELLOW);

        permOverlay.setText((o ? "✅" : "⚠️") + " Overlay / Floating Window");
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
                updateStep(4, "Launching Cloned BGMI...", 100);
                mainHandler.postDelayed(() -> VirtualContainer.getInstance().launch(this, TARGET_PKG), 2000);

            } catch (Exception e) {
                Logger.e(TAG, "Process failed", e);
                mainHandler.post(() -> {
                    cloneStatus.setText("❌ Error: " + e.getMessage());
                    cloneStatus.setTextColor(Color.RED);
                });
            }
        }).start();
    }

    private void performCloning() throws Exception {
        updateCloneStatus("Initializing Sandbox folders...", 10);
        Thread.sleep(800);
        
        updateCloneStatus("Copying target metadata...", 40);
        boolean ok = CloneManager.getInstance().prepareClone(this, TARGET_PKG);
        if (!ok) throw new Exception("Failed to map BGMI metadata.");
        
        updateCloneStatus("Applying package spoofing...", 80);
        Thread.sleep(1000);
        
        updateCloneStatus("✅ Clone Ready!", 100);
    }

    private void performDownload() throws Exception {
        // We use the SDK's downloader but track it
        mainHandler.post(() -> {
            downloadStatus.setText("Downloading libOWNERHUBEE.so...");
            downloadBar.setIndeterminate(true);
        });

        // Simplified for feedback - real downloader would provide callbacks
        Thread.sleep(2000); 
        VirtualContainer.getInstance().downloadAndInject(this, TARGET_PKG, LIB_URL, "modded_esp.so");
        
        mainHandler.post(() -> {
            downloadBar.setIndeterminate(false);
            downloadBar.setProgress(100);
            downloadStatus.setText("✅ Download Complete!");
            downloadSpeed.setText("Speed: N/A (Cached/Success)");
        });
    }

    private void performInjection() throws Exception {
        mainHandler.post(() -> {
            injectionStatus.setText("🔄 Linking Library to Sandbox Namespace...");
        });
        
        // This actually happens inside SandboxActivity, we are just showing the "intent" here
        Thread.sleep(1000);
        
        mainHandler.post(() -> {
            injectionStatus.setText("✅ Injection Queued!");
            pidText.setText("Status: Ready to link in Game Process (PID will show after launch)");
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
