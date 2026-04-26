package com.onecore.loader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Splash Screen with mandatory Permission handling for Games.
 */
public class SplashActivity extends Activity {
    private static final String TAG = "SplashActivity";
    private static final int REQ_PERMS = 1001;
    private static final int REQ_MANAGE_STORAGE = 1002;

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
        
        // Fail-safe timer: if stuck at splash for 5s, try to proceed
        new Handler().postDelayed(() -> {
            if (!isFinishing()) {
                Log.w(TAG, "Splash timeout fallback triggered.");
                proceedToMain();
            }
        }, 5000);

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            java.util.List<String> perms = new java.util.ArrayList<>();
            
            // Storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES);
                perms.add(Manifest.permission.READ_MEDIA_VIDEO);
                perms.add(Manifest.permission.READ_MEDIA_AUDIO);
            } else {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            
            // Critical for BGMI and Device Identity
            perms.add(Manifest.permission.READ_PHONE_STATE);
            
            // Team chat and features
            perms.add(Manifest.permission.RECORD_AUDIO);
            perms.add(Manifest.permission.CAMERA);
            
            // Location
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            
            // Notification (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }

            java.util.List<String> needed = new java.util.ArrayList<>();
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(p);
                }
            }

            if (!needed.isEmpty()) {
                Log.i(TAG, "Requesting mandatory permissions: " + needed.size());
                requestPermissions(needed.toArray(new String[0]), REQ_PERMS);
                return;
            }
        }
        
        // Handle Android 11+ Manage External Storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.i(TAG, "Requesting Manage External Storage...");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQ_MANAGE_STORAGE);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQ_MANAGE_STORAGE);
                }
                return;
            }
        }

        // Overlay permission for ESP/Menus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.i(TAG, "Requesting Overlay Permission...");
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1003);
                return;
            }
        }

        proceedToMain();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            checkAndRequestPermissions();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MANAGE_STORAGE || requestCode == 1003) {
            checkAndRequestPermissions();
        }
    }

    private void proceedToMain() {
        new Handler().postDelayed(() -> {
            if (!isFinishing()) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 800);
    }
}
