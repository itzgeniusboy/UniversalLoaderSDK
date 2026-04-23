package com.onecore.loader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.onecore.sdk.utils.Logger;
import com.onecore.sdk.utils.PermissionsHelper;

/**
 * Splash Screen that enforces permissions before the loader starts.
 * Fixed: Added UI for missing permissions to avoid black screen and loops.
 */
public class SplashActivity extends Activity {
    private static final String TAG = "SplashActivity";
    private LinearLayout permissionLayout;
    private Button grantBtn;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Simple fallback UI for permissions
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(0xFF121212);
        
        statusText = new TextView(this);
        statusText.setText("ONECORE KERNEL INITIALIZING...");
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setPadding(20, 20, 20, 20);
        root.addView(statusText);

        permissionLayout = new LinearLayout(this);
        permissionLayout.setOrientation(LinearLayout.VERTICAL);
        permissionLayout.setGravity(android.view.Gravity.CENTER);
        permissionLayout.setVisibility(View.GONE);
        
        TextView desc = new TextView(this);
        desc.setText("System Permissions Required for OBB/Data Mapping");
        desc.setTextColor(0xFFAAAAAA);
        desc.setPadding(40, 20, 40, 40);
        permissionLayout.addView(desc);

        grantBtn = new Button(this);
        grantBtn.setText("GRANT PERMISSIONS");
        grantBtn.setOnClickListener(v -> PermissionsHelper.requestSpecialPermissions(this));
        permissionLayout.addView(grantBtn);

        root.addView(permissionLayout);
        setContentView(root);
        
        checkAndProceed();
    }

    private void checkAndProceed() {
        if (PermissionsHelper.hasAllPermissions(this)) {
            permissionLayout.setVisibility(View.GONE);
            statusText.setText("ALL SYSTEMS OK. LOADING...");
            provideHapticFeedback();
            startLoaderWithDelay();
        } else {
            permissionLayout.setVisibility(View.VISIBLE);
            statusText.setText("PERMISSIONS CONFIGURATION REQUIRED");
            // Seamless flow: Request the first one automatically
            PermissionsHelper.requestNextPermission(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check again when user returns from settings
        if (PermissionsHelper.hasAllPermissions(this)) {
            checkAndProceed();
        } else {
            statusText.setText("NEXT PERMISSION PENDING...");
            // Automatically prompt for the next one in the chain
            PermissionsHelper.requestNextPermission(this);
        }
    }

    private void provideHapticFeedback() {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(50);
        } catch (Exception ignored) {}
    }

    private void startLoaderWithDelay() {
        new Handler().postDelayed(() -> {
            if (!isFinishing()) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 1000);
    }
}
