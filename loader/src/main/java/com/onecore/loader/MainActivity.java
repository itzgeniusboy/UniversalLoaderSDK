package com.onecore.loader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.onecore.loader.views.GradientButton;

/**
 * Premium iOS-style Dashboard for OneCore Loader.
 * Simplified for minimal working version.
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
            Log.i(TAG, "!! START GAME CLICKED !!");
            provideHapticFeedback();
            launchGame();
        });
    }

    private void startProgressSequence() {
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
                    Log.e(TAG, "Sequential Launch Failed: " + reason);
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
        Log.i(TAG, "launchGame: Initiating Sandbox Launch Sequence.");
        startGameBtn.setEnabled(false);
        startGameBtn.setText("VIRTUALIZING...");
        
        GameLauncher.start(this, new GameLauncher.LaunchCallback() {
            @Override
            public void onProcessDetected(int pid) {
                Log.i(TAG, "Sandbox Session Established.");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "GAME LOADED IN SANDBOX", Toast.LENGTH_SHORT).show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        startGameBtn.setEnabled(true);
                        startGameBtn.setText("START GAME");
                    }, 1000);
                });
            }

            @Override
            public void onFailed(String reason) {
                Log.e(TAG, "Sandbox Engine Error: " + reason);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "VIRTUALIZATION FAILED: " + reason, Toast.LENGTH_LONG).show();
                    Log.i(TAG, "!! FAILSAFE !! Opening game via direct launch...");
                    
                    // Fallback to direct launch
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
                            Log.i(TAG, "!! DIRECT LAUNCH SUCCESS !!");
                        } else {
                            Log.e(TAG, "Fallback Failed: Game not installed.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Direct launch error during failsafe", e);
                    }
                    
                    startGameBtn.setEnabled(true);
                    startGameBtn.setText("START GAME");
                });
            }

            @Override
            public void onProgress(String message) {
                Log.i(TAG, "Engine State: " + message);
            }
        });
    }

    private void provideHapticFeedback() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(50);
            }
        } catch (Exception e) {
            Log.e(TAG, "Haptic feedback error", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
