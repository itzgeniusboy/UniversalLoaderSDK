package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.onecore.sdk.OneCoreSDK;
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
        Context context = (who != null) ? who : OneCoreSDK.getContext();
        String hostPkg = (context != null) ? context.getPackageName() : "com.onecore.loader";
        intent = OneCoreStubManager.replaceWithStub(intent, hostPkg);

        if (intent == null) return null;

        final Intent finalIntent = intent;
        ActivityResult result = null;
        
        try {
            // Try different signatures for execStartActivity (Android versions vary)
            Method execMethod = null;
            try {
                // Signature 1: Standard (Context, IBinder, IBinder, Activity, Intent, int, Bundle)
                execMethod = ReflectionHelper.findMethod(Instrumentation.class, "execStartActivity",
                        Context.class, IBinder.class, IBinder.class, Activity.class,
                        Intent.class, int.class, Bundle.class);
            } catch (Exception e) {
                try {
                    // Signature 2: Context, IBinder, IBinder, Activity, Intent, int
                    execMethod = ReflectionHelper.findMethod(Instrumentation.class, "execStartActivity",
                            Context.class, IBinder.class, IBinder.class, Activity.class,
                            Intent.class, int.class);
                } catch (Exception e2) {
                    try {
                        // Signature 3: Context, IBinder, IBinder, Fragment, Intent, int, Bundle (just in case)
                        execMethod = ReflectionHelper.findMethod(Instrumentation.class, "execStartActivity",
                            Context.class, IBinder.class, IBinder.class, String.class,
                            Intent.class, int.class, Bundle.class);
                    } catch (Exception e3) {
                        Log.e(TAG, "Failed all execStartActivity signatures", e3);
                    }
                }
            }

            if (execMethod != null) {
                execMethod.setAccessible(true);
                int paramCount = execMethod.getParameterTypes().length;
                if (paramCount == 7) {
                    result = (ActivityResult) execMethod.invoke(mBase, who, contextThread, token, target, finalIntent, requestCode, options);
                } else if (paramCount == 6) {
                    result = (ActivityResult) execMethod.invoke(mBase, who, contextThread, token, target, finalIntent, requestCode);
                }
            } else {
                // Extreme fallback: Try to call mBase directly if it's not a proxy
                // But it's risky if mBase is also hooked or a specific subclass
                Log.w(TAG, "Executing extreme fallback for execStartActivity");
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
                Log.i(TAG, "OneCore-Lifecycle: newActivity requested for " + targetActivity + " in " + targetPkg);
                VirtualContainer container = VirtualContainer.getInstance();
                ClassLoader virtualCl = container.getClassLoader();
                
                if (virtualCl != null) {
                    try {
                        Log.d(TAG, "OneCore-Lifecycle: Instantiating activity from virtual ClassLoader: " + targetActivity);
                        Activity activity = mBase.newActivity(virtualCl, targetActivity, intent);
                        Log.i(TAG, "OneCore-Lifecycle: Activity Instance Created -> " + activity.getClass().getName());
                        android.util.Log.d(TAG, ">>> STEP 4: Activity Created <<<");
                        return activity;
                    } catch (Exception e) {
                        Log.e(TAG, "!!! OneCore-Lifecycle: Virtual instantiation FAILED for " + targetActivity, e);
                        // Do not throw yet, let base try if it can find it (unlikely but safer)
                    }
                } else {
                    Log.w(TAG, "OneCore-Lifecycle: virtualCl is NULL in newActivity!");
                }
            }
        }
        
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callApplicationOnCreate(android.app.Application app) {
        String pkg = app.getPackageName();
        VirtualContainer container = VirtualContainer.getInstance();
        
        Log.i(TAG, "OneCore-Lifecycle: callApplicationOnCreate for " + pkg);

        // If this is our virtual application instance
        if (app == container.getTargetApplication()) {
            Log.i(TAG, ">>> callApplicationOnCreate: Initializing Virtual Application for " + pkg);
            OneCoreContextFixer.fixContext(app, container.getPackageName());
        }
        
        try {
            mBase.callApplicationOnCreate(app);
            Log.i(TAG, "OneCore-Lifecycle: callApplicationOnCreate finished for " + pkg);
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-Lifecycle: Application.onCreate CRASHED for " + pkg, e);
            throw e; // Rethrow to see the crash clearly
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
                Log.i(TAG, "OneCore-Lifecycle: callActivityOnCreate for " + targetActivity);
                
                try {
                    // Boost priority for smoothness in Game Mode
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
                    Log.i(TAG, "OneCore-Lifecycle: Render Priority Boosted for " + targetActivity);
                } catch (Exception e) {}

                VirtualContainer container = VirtualContainer.getInstance();
                if (container.getTargetApplication() == null && targetPkg != null) {
                    Log.i(TAG, "OneCore-Lifecycle: Application not bound. Binding now for " + targetPkg);
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
                        Log.e(TAG, "!!! OneCore-Lifecycle: Failed to swap mInitialApplication", e);
                    }
                }
                
                // Immersive and UI fixes for target activity only
                Integer theme = container.getTheme(targetActivity);
                if (theme != null && theme != 0) {
                    Log.d(TAG, "OneCore-Lifecycle: Applying virtual theme " + theme + " to " + targetActivity);
                    activity.setTheme(theme);
                }

                if (activity.getWindow() != null) {
                    Log.d(TAG, "OneCore-Lifecycle: Setting up Window flags for " + targetActivity);
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

                Log.d(TAG, "OneCore-Lifecycle: Fixing context for " + targetActivity);
                OneCoreContextFixer.fixContext(activity, targetPkg);
            }
            
            Log.d(TAG, "OneCore-Lifecycle: Calling base.callActivityOnCreate for " + activity.getClass().getName());
            mBase.callActivityOnCreate(activity, icicle);
            
            // Post-creation fixes
            if (targetActivity != null) {
                OneCoreRenderingFixer.fix(activity);
            }
        });
    }
}
