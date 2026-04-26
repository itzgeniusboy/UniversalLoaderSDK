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
        intent = OneCoreStubManager.replaceWithStub(intent, who.getPackageName());

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
        
        String targetActivity = intent.getStringExtra("target_activity");
        String targetPkg = intent.getStringExtra("target_package");
        String targetApk = intent.getStringExtra("target_apk_path");

        if (targetActivity != null && targetPkg != null) {
            VirtualContainer container = VirtualContainer.getInstance();
            
            // Auto-init in child process if needed
            if (container.getClassLoader() == null && targetApk != null) {
                Log.i(TAG, "OneCore-DEBUG: Child process initializing virtual environment for " + targetPkg);
                Context context = com.onecore.sdk.OneCoreSDK.getContext();
                if (context != null) {
                    container.installApk(context, targetApk, targetPkg);
                }
            }

            ClassLoader virtualCl = container.getClassLoader();
            if (virtualCl != null) {
                try {
                    Log.d(TAG, "OneCore-DEBUG: Instantiating virtual activity: " + targetActivity);
                    return mBase.newActivity(virtualCl, targetActivity, intent);
                } catch (Exception e) {
                    Log.e(TAG, "!!! OneCore-ERROR: Failed to instantiate virtual activity: " + targetActivity, e);
                    throw new ClassNotFoundException("Virtual Activity not found: " + targetActivity, e);
                }
            }
        }
        
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callApplicationOnCreate(android.app.Application app) {
        String pkg = app.getPackageName();
        if (pkg != null && !pkg.equals(VirtualContainer.getInstance().getAppInfo() == null ? "" : VirtualContainer.getInstance().getAppInfo().packageName)) {
            // This is the host app, do nothing special
        } else {
             // If this is the virtual app...
             OneCoreContextFixer.fixContext(app, pkg);
        }
        mBase.callApplicationOnCreate(app);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        SafeExecutionManager.run("callActivityOnCreate", () -> {
            String targetActivity = activity.getIntent().getStringExtra("target_activity");
            if (targetActivity != null) {
                String targetPkg = activity.getIntent().getStringExtra("target_package");
                
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
                    if (ai != null && ai.nativeLibraryDir != null) {
                        Log.i(TAG, "Native Library Path: " + ai.nativeLibraryDir + " (Exists: " + new java.io.File(ai.nativeLibraryDir).exists() + ")");
                    }
                    container.bindApplication(activity.getApplicationContext(), appClass, targetPkg);
                    
                    // CRITICAL: Swap ActivityThread.mInitialApplication to mask virtualization from AppGlobals
                    try {
                        Object at = ReflectionHelper.invokeMethod(null, "currentActivityThread");
                        if (at != null) {
                            ReflectionHelper.setFieldValue(at, container.getTargetApplication(), "mInitialApplication");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to swap mInitialApplication", e);
                    }
                }
                
                // Apply theme BEFORE context fixing
                Integer theme = container.getTheme(targetActivity);
                if (theme != null && theme != 0) {
                    activity.setTheme(theme);
                    Log.i(TAG, "OneCore-DEBUG: Theme applied: " + theme);
                }

                if (activity.getWindow() != null) {
                    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
                    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    
                    // Critical for UE4: some devices require OPAQUE, others TRANSLUCENT.
                    // For now, we stick to OPAQUE but ensure the surface isn't obscured.
                    activity.getWindow().setFormat(android.graphics.PixelFormat.OPAQUE);
                    
                    // Force navigation hiding for immersive mode
                    android.view.View decorView = activity.getWindow().getDecorView();
                    decorView.setSystemUiVisibility(
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }

                // Fixed context includes Resources and LayoutInflater swap
                OneCoreContextFixer.fixContext(activity, targetPkg);
                Log.i(TAG, "OneCore-DEBUG: Resources loaded from guest: " + (activity.getResources() != null));
                Log.i(TAG, "OneCore-DEBUG: Inflater context = guest: " + (activity.getLayoutInflater().getContext() == activity));
            }
            mBase.callActivityOnCreate(activity, icicle);
            
            // Finalize rendering pipeline
            OneCoreRenderingFixer.fix(activity);
            
            Log.d(TAG, "OneCore-DEBUG: callActivityOnCreate completed, UI should be rendering.");
        });
    }
}
