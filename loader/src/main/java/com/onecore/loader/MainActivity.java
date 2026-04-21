package com.onecore.loader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.widget.Toast;
import com.onecore.loader.views.GradientButton;
import com.onecore.sdk.utils.Logger;

/**
 * Premium iOS-style Dashboard for OneCore Loader.
 * Fixed: Now properly triggers GameLauncher to start BGMI.
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private GradientButton launchBtn;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        launchBtn = findViewById(R.id.launchBtn);

        // Start premium pulse animation
        launchBtn.startPulse();
        
        launchBtn.setOnClickListener(v -> {
            provideHapticFeedback();
            // Premium tap animation
            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                initiateLaunch();
            }).start();
        });
    }

    private void initiateLaunch() {
        Logger.i(TAG, "User triggered launch sequence from Dashboard.");
        
        // Show status feedback
        launchBtn.setText("LAUNCHING...");
        launchBtn.setEnabled(false);
        Toast.makeText(this, "INITIALIZING VIRTUAL SESSION", Toast.LENGTH_SHORT).show();

        // Trigger the actual launch through GameLauncher (handles check + injection + launch)
        GameLauncher.start(this, new GameLauncher.LaunchCallback() {
            @Override
            public void onProcessDetected(int pid) {
                Logger.i(TAG, "Virtualization Success: BGMI process is now active.");
                runOnUiThread(() -> {
                    launchBtn.setText("LAUNCH CLONE");
                    launchBtn.setEnabled(true);
                });
            }

            @Override
            public void onFailed(String reason) {
                Logger.e(TAG, "Launch process failed: " + reason);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "FAILED: " + reason, Toast.LENGTH_LONG).show();
                    launchBtn.setText("LAUNCH CLONE");
                    launchBtn.setEnabled(true);
                });
            }

            @Override
            public void onProgress(String message) {
                Logger.d(TAG, "Launch Status: " + message);
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
