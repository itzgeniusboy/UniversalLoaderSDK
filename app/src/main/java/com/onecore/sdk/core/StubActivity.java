package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import android.app.ActivityOptions;
import android.os.Build;

/**
 * Android 14 Sandbox StubActivity.
 * Acts as the entry point for guest applications within the virtual display.
 * Resolves the "Original game opens instead of clone" issue.
 */
public class StubActivity extends Activity {
    private static final String TAG = "OneCore-Stub";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent target = getIntent();
        String realActivity = target.getStringExtra("target_activity");

        if (realActivity != null) {
            try {
                Logger.i(TAG, "Redirecting from Stub to: " + realActivity);
                Intent newIntent = new Intent();
                newIntent.setClassName(this, realActivity);
                newIntent.putExtras(target);
                
                // IMPORTANT: Ensure flags are handled or added if needed
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                startActivity(newIntent);
                finish();

            } catch (Exception e) {
                Logger.e(TAG, "Redirection failed: " + e.getMessage());
                e.printStackTrace();
                finish();
            }
        } else {
            Logger.w(TAG, "StubActivity created without target_activity. Finishing.");
            finish();
        }
    }

    @Override
    public android.content.res.Resources getResources() {
        android.content.res.Resources guestRes = VirtualContainer.getInstance().getGuestResources();
        return guestRes != null ? guestRes : super.getResources();
    }

    @Override
    public ClassLoader getClassLoader() {
        ClassLoader guestLoader = VirtualContainer.getInstance().getGuestClassLoader();
        return guestLoader != null ? guestLoader : super.getClassLoader();
    }
}
