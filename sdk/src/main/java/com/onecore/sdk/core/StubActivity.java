package com.onecore.sdk.core;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * StubActivity used to host the virtualized application components.
 */
public class StubActivity extends Activity {
    private static final String TAG = "StubActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "StubActivity created");
        
        TextView textView = new TextView(this);
        textView.setText("OneCore Virtual Container Active\nWaiting for Component Load...");
        setContentView(textView);
    }
}
