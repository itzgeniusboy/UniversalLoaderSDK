package com.onecore.loader;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.onecore.sdk.OneCoreSDK;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.AndroidVersionCompat;

public class MainActivity extends AppCompatActivity {
    private static final String BGMI_PKG = "com.pubg.imobile";
    private ProgressBar progressBar;
    private Button btnLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Android 15 Edge-to-Edge support
        AndroidVersionCompat.setupEdgeToEdge(this);
        
        setContentView(R.layout.activity_main);

        OneCoreSDK.init(getApplicationContext(), "LOADER_TEST_KEY");

        progressBar = findViewById(R.id.clone_progress);
        btnLaunch = findViewById(R.id.btn_launch);
        Button btnClone = findViewById(R.id.btn_clone);
        TextView statusMode = findViewById(R.id.status_mode);

        statusMode.setText("Mode: " + (isRooted() ? "Root" : "Non-Root"));

        btnClone.setOnClickListener(v -> startCloning());
        btnLaunch.setOnClickListener(v -> launchGame());
    }

    private void startCloning() {
        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Cloning BGMI...", Toast.LENGTH_SHORT).show();
        
        // Mock cloning delay for UI demonstration
        new android.os.Handler().postDelayed(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Clone Successful", Toast.LENGTH_LONG).show();
        }, 3000);
    }

    private void launchGame() {
        Toast.makeText(this, "Injecting & Launching BGMI...", Toast.LENGTH_SHORT).show();
        
        // Use SDK to launch in virtual space
        VirtualContainer.getInstance().launch(this, BGMI_PKG);
        
        // Auto-Injection (DEX/SO)
        // OneCoreSDK.downloadAndInject(BGMI_PKG, "https://github.com/onecore/cheats/raw/main/bgmi.dex", "bgmi_cheat.dex");
    }

    private boolean isRooted() {
        return new java.io.File("/system/bin/su").exists() || new java.io.File("/system/xbin/su").exists();
    }
}
