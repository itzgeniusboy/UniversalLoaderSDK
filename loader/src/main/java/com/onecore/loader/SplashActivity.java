package com.onecore.loader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Splash Screen.
 * Simplified for minimal working version.
 */
public class SplashActivity extends Activity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(0xFF121212);
        
        TextView statusText = new TextView(this);
        statusText.setText("ONECORE INITIALIZING...");
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setPadding(20, 20, 20, 20);
        root.addView(statusText);

        setContentView(root);
        
        new Handler().postDelayed(() -> {
            if (!isFinishing()) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 1000);
    }
}
