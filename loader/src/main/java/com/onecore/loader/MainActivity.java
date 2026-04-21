package com.onecore.loader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.onecore.loader.views.GradientButton;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;

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
            provideHapticFeedback();
            launchGame();
        });
    }

    private void startProgressSequence() {
        launchBtn.setVisibility(View.GONE);
        progressHud.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                // 0-25%: Initializing
                updateProgress(10, "INITIALIZING VIRTUAL SPACE");
                Thread.sleep(800);
                
                // 25-50%: Extraction
                updateProgress(40, "EXTRACTING BINARY DATA");
                Thread.sleep(1000);
                
                // 50-75%: Injection
                updateProgress(65, "INJECTION IN PROGRESS");
                Thread.sleep(1200);
                
                // 75-100%: Environment
                updateProgress(90, "PREPARING GAME ENVIRONMENT");
                Thread.sleep(800);
                
                updateProgress(100, "READY TO PLAY");
                Thread.sleep(300);

                runOnUiThread(() -> {
                    progressHud.setVisibility(View.GONE);
                    startGameBtn.setVisibility(View.VISIBLE);
                    startGameBtn.startPulse();
                    Toast.makeText(MainActivity.this, "ALL SYSTEMS READY", Toast.LENGTH_SHORT).show();
                });

            } catch (InterruptedException e) {
                Logger.e(TAG, "Progress interrupted", e);
            }
        }).start();
    }

    private void updateProgress(int progress, String text) {
        runOnUiThread(() -> {
            launchProgressBar.setProgress(progress);
            launchStepText.setText(text);
        });
    }

    private void launchGame() {
        Logger.i(TAG, "User started game.");
        startGameBtn.setEnabled(false);
        startGameBtn.setText("OPENING...");
        
        // Final handoff to GameLauncher for actual process management
        GameLauncher.start(this, new GameLauncher.LaunchCallback() {
            @Override
            public void onProcessDetected(int pid) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "GAME INITIALIZED", Toast.LENGTH_SHORT).show();
                    // Optional: Close loader or keep background
                });
            }

            @Override
            public void onFailed(String reason) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "START FAILED: " + reason, Toast.LENGTH_LONG).show();
                    startGameBtn.setEnabled(true);
                    startGameBtn.setText("START GAME");
                });
            }

            @Override
            public void onProgress(String message) {
                Logger.d(TAG, "Game Status: " + message);
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
}
