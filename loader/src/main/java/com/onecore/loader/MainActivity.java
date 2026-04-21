package com.onecore.loader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.AlphaAnimation;
import com.onecore.loader.views.GradientButton;
import com.onecore.sdk.utils.Logger;

/**
 * Premium iOS-style Dashboard for OneCore Loader.
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
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                initiateLaunch();
            }).start();
        });
    }

    private void initiateLaunch() {
        try {
            Logger.i(TAG, "Initiating Premium Launch Sequence...");
            Intent intent = new Intent(this, StatusActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            Logger.e(TAG, "Launch failed", e);
        }
    }

    private void provideHapticFeedback() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(50);
            }
        } catch (SecurityException se) {
            Logger.e(TAG, "Vibrate permission missing at runtime.");
        } catch (Exception e) {
            Logger.e(TAG, "Haptic feedback failed", e);
        }
    }
}
