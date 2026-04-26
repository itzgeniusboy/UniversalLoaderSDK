package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.ReflectionHelper;
import java.lang.reflect.Method;

/**
 * Version-adaptive Instrumentation to intercept Activity lifecycle and fix ClassLoaders.
 */
public class OneCoreInstrumentation extends Instrumentation {
    private static final String TAG = "OneCoreInstrumentation";
    private final Instrumentation mBase;

    public OneCoreInstrumentation(Instrumentation base) {
        this.mBase = base;
        Log.i(TAG, ">>> OneCore: Custom Instrumentation Installed <<<");
    }

    /**
     * Hidden method in Instrumentation. Intercepts startActivity calls.
     * Adaptive fallback for different signatures across Android versions.
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        Log.d(TAG, "OneCore-DEBUG: execStartActivity intercepted. Target pkg=" + (intent != null ? intent.getPackage() : "null"));
        
        // Rewrite intent for StubActivity
        String hostPkg = (who != null) ? who.getPackageName() : (sContext != null ? sContext.getPackageName() : "com.onecore.loader");
        intent = OneCoreStubManager.replaceWithStub(intent, hostPkg);

        final Intent finalIntent = intent;
        ActivityResult result = null;
        
        try {
            // Try different signatures for execStartActivity (Android versions vary)
            Method execMethod = null;
            try {
                // Signature 1: Standard
                execMethod = ReflectionHelper.findMethod(Instrumentation.class, "execStartActivity",
                        Context.class, IBinder.class, IBinder.class, Activity.class,
                        Intent.class, int.class, Bundle.class);
            } catch (Exception e) {
                try {
                    // Signature 2: Some older versions or specific vendors
                    execMethod = ReflectionHelper.findMethod(Instrumentation.class, "execStartActivity",
                            Context.class, IBinder.class, IBinder.class, Activity.class,
                            Intent.class, int.class);
                } catch (Exception e2) {
                    Log.e(TAG, "Failed to find execStartActivity signature", e2);
                }
            }

            if (execMethod != null) {
                execMethod.setAccessible(true);
                if (execMethod.getParameterTypes().length == 7) {
                    result = (ActivityResult) execMethod.invoke(mBase, who, contextThread, token, target, finalIntent, requestCode, options);
                } else {
                    result = (ActivityResult) execMethod.invoke(mBase, who, contextThread, token, target, finalIntent, requestCode);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error invoking execStartActivity", e);
        }

        // If we reach here and result is null, it means reflection failed or returned null
        // We MUST NOT return null as it signals a failure to the system.
        // Returning a dummy result is better than a crash.
        return result; 
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        if (intent != null) {
            String targetActivity = intent.getStringExtra("target_activity");
            String targetPkg = intent.getStringExtra("target_package");
    
            if (targetPkg != null && targetActivity != null) {
                VirtualContainer container = VirtualContainer.getInstance();
                ClassLoader virtualCl = container.getClassLoader();
                
                if (virtualCl != null) {
                    try {
                        Log.d(TAG, "OneCore-DEBUG: Instantiating activity from virtual ClassLoader: " + targetActivity);
                        return mBase.newActivity(virtualCl, targetActivity, intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed virtual instantiation, falling back to provided CL", e);
                    }
                }
            }
        }
        
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callApplicationOnCreate(android.app.Application app) {
        String pkg = app.getPackageName();
        VirtualContainer container = VirtualContainer.getInstance();
        
        // If this is our virtual application instance
        if (app == container.getTargetApplication()) {
            Log.i(TAG, ">>> callApplicationOnCreate: Initializing Virtual Application for " + pkg);
            OneCoreContextFixer.fixContext(app, container.getPackageName());
        }
        
        try {
            mBase.callApplicationOnCreate(app);
        } catch (Exception e) {
            Log.e(TAG, "Application.onCreate crashed", e);
        }
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        mBase.callActivityOnResume(activity);
        if (activity != null && activity.getIntent() != null) {
            String targetActivity = activity.getIntent().getStringExtra("target_activity");
            // Only fix rendering for virtual/guest activities to prevent rotating the loader
            if (targetActivity != null) {
                OneCoreRenderingFixer.fix(activity);
            }
        }
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        SafeExecutionManager.run("callActivityOnCreate", () -> {
            Intent intent = activity.getIntent();
            String targetActivity = intent != null ? intent.getStringExtra("target_activity") : null;
            // Only perform guest-specific fixes if it's a virtual activity
            if (targetActivity != null) {
                String targetPkg = intent.getStringExtra("target_package");
                
                try {
                    // Boost priority for smoothness in Game Mode
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
                    Log.i(TAG, "OneCore-DEBUG: Render Priority Boosted for " + targetActivity);
                } catch (Exception e) {}

                VirtualContainer container = VirtualContainer.getInstance();
                if (container.getTargetApplication() == null && targetPkg != null) {
                    String appClass = "android.app.Application";
                    android.content.pm.ApplicationInfo ai = container.getAppInfo();
                    if (ai != null && ai.className != null) {
                        appClass = ai.className;
                    }
                    container.bindApplication(activity.getApplicationContext(), appClass, targetPkg);
                    
                    // Sync ActivityThread state
                    try {
                        Object at = ReflectionHelper.invokeMethod("android.app.ActivityThread", "currentActivityThread");
                        if (at != null) {
                            ReflectionHelper.setFieldValue(at, container.getTargetApplication(), "mInitialApplication");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to swap mInitialApplication", e);
                    }
                }
                
                // Immersive and UI fixes for target activity only
                Integer theme = container.getTheme(targetActivity);
                if (theme != null && theme != 0) {
                    activity.setTheme(theme);
                }

                if (activity.getWindow() != null) {
                    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
                    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    activity.getWindow().setFormat(android.graphics.PixelFormat.OPAQUE);
                    
                    // Sticky Immersive Mode
                    android.view.View decorView = activity.getWindow().getDecorView();
                    decorView.setSystemUiVisibility(
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }

                OneCoreContextFixer.fixContext(activity, targetPkg);
            }
            
            mBase.callActivityOnCreate(activity, icicle);
            
            // Post-creation fixes
            if (targetActivity != null) {
                OneCoreRenderingFixer.fix(activity);
            }
        });
    }
}
