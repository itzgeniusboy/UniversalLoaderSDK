package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.core.reflex.ReflectionHelper;
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
        if (targetActivity != null) {
            ClassLoader virtualCl = VirtualContainer.getInstance().getClassLoader();
            if (virtualCl != null) {
                try {
                    Log.d(TAG, "OneCore-DEBUG: Instantiating virtual activity: " + targetActivity);
                    return mBase.newActivity(virtualCl, targetActivity, intent);
                } catch (Exception e) {
                    Log.e(TAG, "!!! OneCore-ERROR: Failed to instantiate virtual activity: " + targetActivity, e);
                    // Critical failure: if we can't load the virtual activity, we can't proceed
                    throw new ClassNotFoundException("Virtual Activity not found: " + targetActivity, e);
                }
            }
        }
        
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        SafeExecutionManager.run("callActivityOnCreate", () -> {
            String targetActivity = activity.getIntent().getStringExtra("target_activity");
            if (targetActivity != null) {
                String targetPkg = activity.getIntent().getStringExtra("target_package");
                
                VirtualContainer container = VirtualContainer.getInstance();
                if (container.getTargetApplication() == null && targetPkg != null) {
                     container.bindApplication(activity.getApplicationContext(), "android.app.Application", targetPkg);
                }
                
                // Apply theme BEFORE context fixing
                Integer theme = container.getTheme(targetActivity);
                if (theme != null && theme != 0) {
                    activity.setTheme(theme);
                }

                // Fixed context includes Resources and LayoutInflater swap
                OneCoreContextFixer.fixContext(activity, targetPkg);
            }
            mBase.callActivityOnCreate(activity, icicle);
        });
    }
}
