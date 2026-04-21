package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * The Component Host. 
 * Acts as a proxy to execute Guest Activity lifecycle in a system-registered container.
 */
public class StubActivity extends Activity {
    private static final String TAG = "StubActivity";
    private Activity guestActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        String targetActivity = getIntent().getStringExtra("target_activity");
        String targetPackage = getIntent().getStringExtra("target_package");

        try {
            Logger.d(TAG, "Proxying Guest Activity: " + targetActivity);
            
            // 1. Load the guest class using the Sandbox ClassLoader
            Class<?> guestClass = getClassLoader().loadClass(targetActivity);
            Constructor<?> constructor = guestClass.getConstructor();
            guestActivity = (Activity) constructor.newInstance();

            // 2. Relay the Lifecycle (Manual Injection)
            // In a production SDK this is handled via Instrumentation hooks, 
            // but here we manually trigger onCreate with Stub context.
            Method setIntent = Activity.class.getDeclaredMethod("setIntent", Intent.class);
            setIntent.setAccessible(true);
            setIntent.invoke(guestActivity, getIntent());

            // 3. Trigger Guest onCreate
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            onCreate.setAccessible(true);
            onCreate.invoke(guestActivity, savedInstanceState);
            
            // 4. Load library in Guest context
            String libPath = getIntent().getStringExtra("library_path");
            if (libPath != null && new File(libPath).exists()) {
                System.load(libPath);
                Logger.i(TAG, "ESP Library linked in STUB context.");
            }

            Logger.i(TAG, "Guest Activity Lifecycle ATTACHED to Stub.");

        } catch (Exception e) {
            Logger.e(TAG, "Failed to proxy guest activity", e);
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (guestActivity != null) try {
            Method m = Activity.class.getDeclaredMethod("onStart");
            m.setAccessible(true);
            m.invoke(guestActivity);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (guestActivity != null) try {
            Method m = Activity.class.getDeclaredMethod("onResume");
            m.setAccessible(true);
            m.invoke(guestActivity);
        } catch (Exception ignored) {}
    }
}
