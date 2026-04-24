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
    private Activity targetActivity;
    private boolean isProxy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Task 3: ClassLoader Binding
        ClassLoader loader = VirtualContainer.getInstance().getGuestClassLoader();
        if (loader != null) {
            Thread.currentThread().setContextClassLoader(loader);
        }

        super.onCreate(savedInstanceState);
        
        // Setup Transparent UI
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        String targetPackage = getIntent().getStringExtra("target_package");
        String targetActivityClass = getIntent().getStringExtra("target_activity");
        
        if (targetPackage == null || targetActivityClass == null) {
            Logger.e(TAG, "Missing launch metadata.");
            finish();
            return;
        }

        Logger.i(TAG, "Virtual startup for: " + targetActivityClass);

        // Task 4: StubActivity Upgrade
        try {
            if (loader == null) {
                Logger.e(TAG, "Guest ClassLoader is NULL.");
                finish();
                return;
            }

            Class<?> clazz = loader.loadClass(targetActivityClass);
            targetActivity = (Activity) clazz.newInstance();
            isProxy = true;
            
            // Inject StubActivity's context and window into targetActivity
            injectContextAndWindow(targetActivity);
            
            // Task 7: Rendering Fix - Inject resources
            patchActivityResources(targetActivity);
            
            // Task 4: Manually invoke lifecycle
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            onCreate.setAccessible(true);
            
            Logger.i(TAG, "Triggering Guest Lifecycle...");
            onCreate.invoke(targetActivity, savedInstanceState);
            
            Logger.i(TAG, "Handover SUCCESS.");

        } catch (Exception e) {
            Logger.e(TAG, "Lifecycle Failure: " + e.getMessage(), e);
            finish();
        }
    }

    private void injectContextAndWindow(Activity activity) {
        try {
            // Task 7: Rendering Fix - Link window for setContentView to work
            Field mWindowField = Activity.class.getDeclaredField("mWindow");
            mWindowField.setAccessible(true);
            mWindowField.set(activity, getWindow());
            
            // Link Base Context or use reflection to set mBase
            Field mBaseField = android.content.ContextWrapper.class.getDeclaredField("mBase");
            mBaseField.setAccessible(true);
            mBaseField.set(activity, this);
            
            // Application instance
            Field mApplicationField = Activity.class.getDeclaredField("mApplication");
            mApplicationField.setAccessible(true);
            mApplicationField.set(activity, getApplication());

            Logger.d(TAG, "Window and Context Injected to Guest Activity.");
        } catch (Exception e) {
            Logger.e(TAG, "Injection Error: " + e.getMessage());
        }
    }

    private void patchActivityResources(Activity activity) {
        try {
            android.content.res.Resources guestRes = VirtualContainer.getInstance().getGuestResources();
            if (guestRes != null) {
                Field mResourcesField = android.view.ContextThemeWrapper.class.getDeclaredField("mResources");
                mResourcesField.setAccessible(true);
                mResourcesField.set(activity, guestRes);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Resource Patching Failed: " + e.getMessage());
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

    @Override
    protected void onResume() {
        super.onResume();
        if (isProxy && targetActivity != null) {
            try {
                Method onResume = Activity.class.getDeclaredMethod("onResume");
                onResume.setAccessible(true);
                onResume.invoke(targetActivity);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        if (isProxy && targetActivity != null) {
            try {
                Method onDestroy = Activity.class.getDeclaredMethod("onDestroy");
                onDestroy.setAccessible(true);
                onDestroy.invoke(targetActivity);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
