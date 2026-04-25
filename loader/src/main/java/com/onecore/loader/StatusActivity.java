package com.onecore.loader;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.onecore.loader.views.StepIndicator;

/**
 * Detailed status activity.
 * Simplified for minimal working version.
 */
public class StatusActivity extends Activity {
    private static final String TAG = "StatusActivity";

    private StepIndicator stepIndicator;
    private TextView cloneStatus, downloadStatus, injectionStatus, pidText;
    private ProgressBar cloneBar, downloadBar;
    private Button overlayBtn;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);

        initViews();
        startSequence();
    }

    private void initViews() {
        stepIndicator = findViewById(R.id.stepIndicator);
        cloneStatus = findViewById(R.id.cloneStatusText);
        downloadStatus = findViewById(R.id.downloadStatusText);
        injectionStatus = findViewById(R.id.injectionStatusText);
        pidText = findViewById(R.id.pidText);
        cloneBar = findViewById(R.id.cloneProgressBar);
        downloadBar = findViewById(R.id.downloadProgressBar);
        overlayBtn = findViewById(R.id.grantOverlayBtn);
    }

    private void startSequence() {
        Log.i(TAG, "Starting sequence...");
        updateStep(1, "Cloning Process Simulation", 100);
        
        mainHandler.postDelayed(() -> {
            updateStep(2, "Download Simulation", 100);
            
            mainHandler.postDelayed(() -> {
                updateStep(3, "Injection Simulation", 100);
                
                mainHandler.postDelayed(() -> {
                    updateStep(4, "READY", 100);
                    pidText.setText("Status: SIMULATED_READY");
                    pidText.setTextColor(Color.GREEN);
                }, 1000);
            }, 1000);
        }, 1000);
    }

    private void updateStep(int step, String text, int barProgress) {
        mainHandler.post(() -> {
            if (stepIndicator != null) stepIndicator.setStep(step);
            if (cloneStatus != null) cloneStatus.setText(text);
            if (cloneBar != null) cloneBar.setProgress(barProgress);
        });
    }
}
