package com.onecore.loader;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * License Activity.
 * Simplified for minimal working version.
 */
public class LicenseActivity extends AppCompatActivity {
    private EditText keyInput;
    private Button btnVerify;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);

        keyInput = findViewById(R.id.license_input);
        btnVerify = findViewById(R.id.btn_verify);
        
        btnVerify.setOnClickListener(v -> {
            String key = keyInput.getText().toString();
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter License Key", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Toast.makeText(this, "License Verified Simulation", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
