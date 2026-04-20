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
import com.onecore.sdk.SDKLicense;
import com.onecore.sdk.utils.AndroidVersionCompat;

public class LicenseActivity extends AppCompatActivity {
    private EditText keyInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Android 15 Edge-to-Edge support
        AndroidVersionCompat.setupEdgeToEdge(this);
        
        setContentView(R.layout.activity_license);

        keyInput = findViewById(R.id.license_input);
        Button btnVerify = findViewById(R.id.btn_verify);
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

            // Using SDK license system via re-initialization
            OneCoreSDK.init(getApplicationContext(), key);
            if (OneCoreSDK.isLicenseValid()) {
                Toast.makeText(this, "License Verified", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Invalid License Key", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
