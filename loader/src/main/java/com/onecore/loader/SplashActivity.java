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

    private TextView statusText;
    private int currentStep = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(0xFF000000);
        
        // Progress logo or initial
        TextView logo = new TextView(this);
        logo.setText("ONECORE");
        logo.setTextSize(32);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        logo.setTextColor(0xFF39FF14);
        logo.setLetterSpacing(0.2f);
        root.addView(logo);

        statusText = new TextView(this);
        statusText.setText("PREPARING SECURITY ENGINE...");
        statusText.setTextColor(0x88FFFFFF);
        statusText.setTextSize(10);
        statusText.setPadding(0, 40, 0, 0);
        statusText.setLetterSpacing(0.1f);
        root.addView(statusText);

        setContentView(root);
        
        // Fail-safe timer
        new Handler().postDelayed(() -> {
            if (!isFinishing() && currentStep < 5) {
                Log.w(TAG, "Splash semi-stuck, manually checking permissions.");
                checkNextPermission();
            }
        }, 3000);

        new Handler().postDelayed(this::checkOBB, 1500);
        new Handler().postDelayed(this::checkNextPermission, 400);
    }

    private void checkOBB() {
        try {
            // Check for common PUBG/BGMI variants
            String[] packages = {
                "com.pubg.imobile", 
                "com.tencent.ig", 
                "com.tencent.tmgp.pubgmhd", 
                "com.pubg.krmobile",
                "com.vng.pubgmobile",
                "com.rekoo.pubgm"
            };
            
            java.io.File obbRoot = new java.io.File(Environment.getExternalStorageDirectory(), "Android/obb");
            if (!obbRoot.exists()) {
                Log.e(TAG, "Critical: /Android/obb root not found!");
                return;
            }

            boolean found = false;
            for (String pkg : packages) {
                java.io.File obbDir = new java.io.File(obbRoot, pkg);
                if (obbDir.exists()) {
                    Log.i(TAG, "Found OBB Directory: " + pkg);
                    // Even if we can't list files due to Android 11+ restrictions, 
                    // if the folder exists, it's a good sign.
                    if (obbDir.isDirectory()) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                // Last ditch effort: list all folders and see if any look like PUBG
                java.io.File[] files = obbRoot.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        if (f.isDirectory() && (f.getName().contains("pubg") || f.getName().contains("tencent"))) {
                            Log.i(TAG, "Detected potential OBB folder: " + f.getName());
                            found = true;
                            break;
                        }
                    }
                }
            }

            if (!found) {
                Toast.makeText(this, "LOG: No matching OBB folder found in /Android/obb/. Please check folder name.", Toast.LENGTH_LONG).show();
            } else {
                Log.i(TAG, "OBB detection successful.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking OBB", e);
        }
    }

    private void checkNextPermission() {
        if (isFinishing()) return;

        // Ensure we are on UI thread
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Step 1: Storage
                if (currentStep == 0) {
                    statusText.setText("SYNCING STORAGE...");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.READ_MEDIA_VIDEO,
                                    Manifest.permission.READ_MEDIA_AUDIO
                            }, REQ_PERMS);
                            return;
                        }
                    } else {
                        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                            }, REQ_PERMS);
                            return;
                        }
                    }
                    currentStep++;
                }

                // Step 2: Device Identity (Phone)
                if (currentStep == 1) {
                    statusText.setText("VIRTUALIZING DEVICE IDENTIFIERS...");
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_PERMS);
                        return;
                    }
                    currentStep++;
                }

                // Step 3: Media & Audio
                if (currentStep == 2) {
                    statusText.setText("BOOTING MEDIA SUBSYSTEMS...");
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, REQ_PERMS);
                        return;
                    }
                    currentStep++;
                }

                // Step 4: Manage Storage (Special)
                if (currentStep == 3) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        statusText.setText("AUTHORIZING FILE-SYSTEM ACCESS...");
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQ_MANAGE_STORAGE);
                        } catch (Exception e) {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivityForResult(intent, REQ_MANAGE_STORAGE);
                            } catch (Exception fatal) {
                                currentStep++;
                                checkNextPermission();
                            }
                        }
                        return;
                    }
                    currentStep++;
                }

                // Step 5: Overlay
                if (currentStep == 4) {
                    if (!Settings.canDrawOverlays(this)) {
                        statusText.setText("INITIALIZING OVERLAY ENGINE...");
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, 1003);
                        } catch (Exception e) {
                            currentStep++;
                            checkNextPermission();
                        }
                        return;
                    }
                    currentStep++;
                }
            }

            statusText.setText("SECURITY ENGINE ONLINE");
            statusText.setTextColor(0xFF39FF14);
            new Handler().postDelayed(this::proceedToMain, 300);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            checkNextPermission();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        checkNextPermission();
    }

    private void proceedToMain() {
        checkOBB();
        new Handler().postDelayed(() -> {
            if (!isFinishing()) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 800);
    }
}
