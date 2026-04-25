package com.onecore.loader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.TextView;
import com.onecore.sdk.VirtualContainer;

/**
 * Proxy Activity that hosts the virtualized application component.
 */
public class StubActivity extends Activity {
    private static final String TAG = "StubActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "!!! OneCore CRITICAL: StubActivity reached onCreate! Swap failed. !!!");
        
        TextView tv = new TextView(this);
        tv.setText("V_CORE ERROR: Activity Swap Failed.\nCheck logs for newActivity() status.");
        tv.setTextColor(0xFFFF0000);
        setContentView(tv);
        
        // finish(); // Commented out so user can see error on screen if it fails
    }
}
