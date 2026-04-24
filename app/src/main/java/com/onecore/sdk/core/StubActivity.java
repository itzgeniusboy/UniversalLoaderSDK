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
        // This is a placeholder. Instrumentation will replace this activity before it ever fully starts.
        // If we reach here, it means redirection failed.
        Logger.w(TAG, "Redirection failed: StubActivity.onCreate reached!");
        finish();
    }
}
