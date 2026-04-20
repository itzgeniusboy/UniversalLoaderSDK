package com.onecore.loader;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.onecore.sdk.OneCoreSDK;
import com.onecore.sdk.SDKLicense;
import com.onecore.sdk.utils.AndroidVersionCompat;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Android 15 Edge-to-Edge support
        AndroidVersionCompat.setupEdgeToEdge(this);
        
        // Simple dark background for splash
        android.view.View splashView = new android.view.View(this);
        splashView.setBackgroundColor(0xFF050505);
        setContentView(splashView);

        // Deferred SDK initialization to ensure UI shows up instantly (< 1s)
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Initialize OneCore SDK in background if needed
                OneCoreSDK.init(getApplicationContext(), ""); 
            } catch (Exception e) {
                // Safe initializer fallback
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (SDKLicense.getInstance().isLicensed()) {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                } else {
                    startActivity(new Intent(SplashActivity.this, LicenseActivity.class));
                }
                finish();
            } catch (Exception e) {
                // Global fallback to License activity if anything fails
                startActivity(new Intent(SplashActivity.this, LicenseActivity.class));
                finish();
            }
        }, 1500);
    }
}
