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

        // Start premium animations
        launchBtn.startPulse();
        applyEntranceAnimation(findViewById(android.R.id.content));

        launchBtn.setOnClickListener(v -> {
            provideHapticFeedback();
            Logger.i(TAG, "Initiating Sandbox Launch Sequence...");
            // Move to StatusActivity for detailed progress
            Intent intent = new Intent(this, StatusActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        findViewById(R.id.navSettings).setOnClickListener(v -> {
            provideHapticFeedback();
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        });
    }

    private void applyEntranceAnimation(View view) {
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        view.startAnimation(fadeIn);
    }

    private void provideHapticFeedback() {
        if (vibrator != null) {
            vibrator.vibrate(50); // Light haptic tap
        }
    }
}
