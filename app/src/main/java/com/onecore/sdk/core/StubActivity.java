package com.onecore.sdk.core;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import com.onecore.sdk.utils.Logger;

/**
 * The Component Host. 
 * This activity is registered in the Manifest and acts as a shell 
 * to host guest activity segments.
 */
public class StubActivity extends Activity {
    private static final String TAG = "StubActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make it full screen for game experience
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        Logger.d(TAG, "Stub Process Active. Swapping namespaces...");
        
        // This is where we hand over the window to the Guest Activity
        // via reflection of its onCreate method.
    }
}
