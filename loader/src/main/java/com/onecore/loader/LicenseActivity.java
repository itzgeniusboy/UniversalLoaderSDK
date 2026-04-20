package com.onecore.loader;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.onecore.sdk.OneCoreSDK;
import com.onecore.sdk.utils.AndroidVersionCompat;
import com.onecore.sdk.utils.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LicenseActivity extends AppCompatActivity {
    private EditText keyInput;
    private Button btnVerify;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Android 15 Edge-to-Edge support
        AndroidVersionCompat.setupEdgeToEdge(this);
        
        setContentView(R.layout.activity_license);

        keyInput = findViewById(R.id.license_input);
        btnVerify = findViewById(R.id.btn_verify);
        Button btnPaste = findViewById(R.id.btn_paste);
        
        findViewById(R.id.tg_link).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/onecore_sdk"));
            startActivity(i);
        });

        btnPaste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                keyInput.setText(clipboard.getPrimaryClip().getItemAt(0).getText());
            }
        });

        btnVerify.setOnClickListener(v -> {
            String key = keyInput.getText().toString();
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter License Key", Toast.LENGTH_SHORT).show();
                return;
            }
            
            verifyLicenseExternally(key);
        });
    }

    private void verifyLicenseExternally(String key) {
        btnVerify.setEnabled(false);
        btnVerify.setText("Verifying...");
        
        executor.execute(() -> {
            boolean isValid = false;
            String errorMessage = "Invalid License Key";
            
            try {
                // The new server URL
                String urlString = "https://darkdevel.dynamicflash.xyz/connect?key=" + key;
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    String result = response.toString().toLowerCase();
                    Logger.d("LicenseActivity", "Server Response: " + result);
                    
                    // Handle different response formats (JSON or Plain Text)
                    if (result.contains("true") || result.contains("success") || result.contains("valid") || result.contains("1")) {
                        isValid = true;
                    }
                } else {
                    errorMessage = "Server Error: " + responseCode;
                }
            } catch (Exception e) {
                Logger.e("LicenseActivity", "Verification error", e);
                errorMessage = "Network Error: " + e.getMessage();
            }

            boolean finalIsValid = isValid;
            String finalErrorMessage = errorMessage;
            
            mainHandler.post(() -> {
                btnVerify.setEnabled(true);
                btnVerify.setText("Verify");
                
                if (finalIsValid) {
                    // Initialize SDK with the key to persist it
                    OneCoreSDK.init(getApplicationContext(), key);
                    
                    Toast.makeText(this, "License Verified Successfully", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                } else {
                    Toast.makeText(this, finalErrorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
