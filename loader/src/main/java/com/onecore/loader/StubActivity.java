package com.onecore.loader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import com.onecore.sdk.VirtualContainer;

/**
 * Proxy Activity that hosts the virtualized application component.
 */
public class StubActivity extends Activity {
    private static final String TAG = "StubActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "StubActivity onCreate (should be intercepted by OneCoreInstrumentation)");
        finish(); // Failsafe if not intercepted
    }
}
