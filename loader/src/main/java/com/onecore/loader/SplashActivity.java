package com.onecore.loader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import com.onecore.sdk.utils.Logger;
import com.onecore.sdk.utils.PermissionsHelper;

/**
 * Splash Screen that enforces permissions before the loader starts.
 */
public class SplashActivity extends Activity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if permissions are granted
        if (!PermissionsHelper.hasAllPermissions(this)) {
            Logger.i(TAG, "Permissions missing. Redirecting to settings...");
            Toast.makeText(this, "Please grant ALL permissions to enable ESP.", Toast.LENGTH_LONG).show();
            PermissionsHelper.requestSpecialPermissions(this);
            // We don't finish here, user needs to grant and come back or restart
        } else {
            Logger.i(TAG, "All permissions granted. Loading...");
            startLoaderWithDelay();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check again when user returns from settings
        if (PermissionsHelper.hasAllPermissions(this)) {
            startLoaderWithDelay();
        }
    }

    private void startLoaderWithDelay() {
        new Handler().postDelayed(() -> {
            // Start the Premium iOS Dashboard
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 1500);
    }
}
