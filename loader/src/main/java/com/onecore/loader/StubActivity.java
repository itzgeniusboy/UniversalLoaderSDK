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
        Log.i(TAG, "StubActivity starting...");

        String targetActivity = getIntent().getStringExtra("target_activity");
        if (targetActivity == null) {
            Toast.makeText(this, "No target activity specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            ClassLoader cl = VirtualContainer.getInstance().getClassLoader();
            if (cl == null) {
                throw new IllegalStateException("ClassLoader is null. Container not initialized?");
            }

            Class<?> clazz = cl.loadClass(targetActivity);
            Log.i(TAG, "Successfully loaded target class: " + clazz.getName());
            
            // In a full virtualization engine, we would use instrumentation to wrap this.
            // For Phase 1, we just log that we reached the entry point via reflection.
            Toast.makeText(this, "Virtual Entry: " + clazz.getSimpleName(), Toast.LENGTH_LONG).show();
            
            // Future implementation: 
            // Activity activity = (Activity) clazz.newInstance();
            // activity.onCreate(savedInstanceState);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load target activity", e);
            Toast.makeText(this, "Virtual Load Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
