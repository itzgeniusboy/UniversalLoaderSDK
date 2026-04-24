package com.onecore.loader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.Settings;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.onecore.loader.views.GradientButton;
import com.onecore.sdk.OneCoreSDK;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import com.onecore.sdk.utils.PermissionsHelper;

/**
 * Premium iOS-style Dashboard for OneCore Loader.
 * Fixed: Sequential 100% progress before "START GAME".
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private GradientButton launchBtn, startGameBtn;
    private LinearLayout progressHud;
    private ProgressBar launchProgressBar;
    private TextView launchStepText;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        launchBtn = findViewById(R.id.launchBtn);
        startGameBtn = findViewById(R.id.startGameBtn);
        progressHud = findViewById(R.id.progressHud);
        launchProgressBar = findViewById(R.id.launchProgressBar);
        launchStepText = findViewById(R.id.launchStepText);

        // Verify SDK Engine is initialized at boot
        if (!OneCoreSDK.isInitialized()) {
            Toast.makeText(this, "CORE ENGINE BOOT ERROR", Toast.LENGTH_LONG).show();
            Logger.e(TAG, "FATAL: OneCoreSDK failed to initialize at Application level.");
        } else if (!OneCoreSDK.isLicenseValid()) {
            Toast.makeText(this, "LICENSE VERIFICATION PENDING", Toast.LENGTH_SHORT).show();
            Logger.w(TAG, "SDK License check in progress or failed.");
        }

        // Start premium pulse animation
        launchBtn.startPulse();
        
        launchBtn.setOnClickListener(v -> {
            provideHapticFeedback();
            // Tap animation
            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                startProgressSequence();
            }).start();
        });

        startGameBtn.setOnClickListener(v -> {
            Logger.i(TAG, "!! START GAME CLICKED !!");
            if (!PermissionsHelper.hasOverlayPermission(this)) {
                Logger.w(TAG, "Missing Overlay Permission. Prompting user.");
                Toast.makeText(this, "PLEASE ALLOW OVERLAY PERMISSION", Toast.LENGTH_SHORT).show();
                PermissionsHelper.requestSpecialPermissions(this);
                return;
            }
            if (!PermissionsHelper.hasStoragePermission(this)) {
                Logger.w(TAG, "Missing Storage Permission. Prompting user.");
                Toast.makeText(this, "PLEASE ALLOW ALL FILES ACCESS", Toast.LENGTH_SHORT).show();
                PermissionsHelper.requestSpecialPermissions(this);
                return;
            }
            
            Logger.i(TAG, "License Check: SIMULATED SUCCESS [DEV-BYPASS]");
            provideHapticFeedback();
            launchGame();
        });
    }

    private void startProgressSequence() {
        if (!PermissionsHelper.hasStoragePermission(this)) {
            Toast.makeText(this, "STORAGE PERMISSION REQUIRED FOR INSTALLATION", Toast.LENGTH_LONG).show();
            PermissionsHelper.requestSpecialPermissions(this);
            return;
        }
        
        launchBtn.setVisibility(View.GONE);
        progressHud.setVisibility(View.VISIBLE);
        
        // Use real LaunchManager to sequentialize: Init -> License -> Install
        LaunchManager.getInstance(this).start("ONECORE-PREMIUM-X782-99", new LaunchManager.LaunchListener() {
            @Override
            public void onProgress(int progress, String message) {
                updateProgress(progress, message);
            }

            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    progressHud.setVisibility(View.GONE);
                    startGameBtn.setVisibility(View.VISIBLE);
                    startGameBtn.startPulse();
                    Toast.makeText(MainActivity.this, "ALL SYSTEMS READY", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailed(String reason) {
                runOnUiThread(() -> {
                    progressHud.setVisibility(View.GONE);
                    launchBtn.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "LAUNCH ABORTED: " + reason, Toast.LENGTH_LONG).show();
                    Logger.e(TAG, "Sequential Launch Failed: " + reason);
                });
            }
        });
    }

    private void updateProgress(int progress, String text) {
        runOnUiThread(() -> {
            launchProgressBar.setProgress(progress);
            launchStepText.setText(text);
        });
    }

    private void launchGame() {
        Logger.i(TAG, "launchGame: Initiating Sandbox Launch Sequence.");
        startGameBtn.setEnabled(false);
        startGameBtn.setText("VIRTUALIZING...");
        
        GameLauncher.start(this, new GameLauncher.LaunchCallback() {
            @Override
            public void onProcessDetected(int pid) {
                Logger.i(TAG, "Sandbox Session Established. Host: com.pubg.imobile [PID: " + pid + "]");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "BGMI LOADED IN SANDBOX", Toast.LENGTH_SHORT).show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        startGameBtn.setEnabled(true);
                        startGameBtn.setText("START GAME");
                    }, 1000);
                });
            }

            @Override
            public void onFailed(String reason) {
                Logger.e(TAG, "Sandbox Engine Error: " + reason);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "VIRTUALIZATION FAILED: " + reason, Toast.LENGTH_LONG).show();
                    Logger.i(TAG, "!! FAILSAFE !! Opening game via direct launch...");
                    
                    // Task 5: Fallback to direct launch
                    try {
                        String pkg = "com.pubg.imobile";
                        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (intent == null) {
                            pkg = "com.pubg.bgmi";
                            intent = getPackageManager().getLaunchIntentForPackage(pkg);
                        }
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            Logger.i(TAG, "!! DIRECT LAUNCH SUCCESS !! Game process should now be starting.");
                        } else {
                            Logger.e(TAG, "Fallback Failed: Game not installed.");
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "Direct launch error during failsafe", e);
                    }
                    
                    startGameBtn.setEnabled(true);
                    startGameBtn.setText("START GAME");
                });
            }

            @Override
            public void onProgress(String message) {
                Logger.i(TAG, "Engine State: " + message);
            }
        });
    }

    private void provideHapticFeedback() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(50);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Haptic feedback error", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Force kill all app processes on exit for total isolation cleanup
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
