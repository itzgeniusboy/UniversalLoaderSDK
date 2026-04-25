package com.onecore.sdk.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Advanced Instrumentation to intercept Activity lifecycle and fix ClassLoaders.
 */
public class OneCoreInstrumentation extends Instrumentation {
    private static final String TAG = "OneCoreInstrumentation";
    private final Instrumentation mBase;

    public OneCoreInstrumentation(Instrumentation base) {
        this.mBase = base;
        Log.i(TAG, ">>> OneCore: Custom Instrumentation Installed (base=" + (base != null ? base.getClass().getName() : "null") + ") <<<");
    }

    /**
     * Hidden method in Instrumentation. Intercepts startActivity calls.
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        Log.d(TAG, "OneCore-DEBUG: execStartActivity intercepted. Intent=" + intent);
        
        // Rewrite intent for StubActivity
        intent = OneCoreStubManager.replaceWithStub(intent, who.getPackageName());

        try {
            Method execMethod = Instrumentation.class.getDeclaredMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class);
            execMethod.setAccessible(true);
            return (ActivityResult) execMethod.invoke(mBase, who, contextThread, token, target, intent, requestCode, options);
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: execStartActivity redirection FAILED !!!", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        Log.i(TAG, "OneCore-DEBUG: newActivity -> " + className);
        
        String targetActivity = intent.getStringExtra("target_activity");
        if (targetActivity != null) {
            Log.i(TAG, "OneCore-DEBUG: Restoring Target Activity [" + targetActivity + "]");
            ClassLoader virtualCl = VirtualContainer.getInstance().getClassLoader();
            
            if (virtualCl != null) {
                try {
                    Log.d(TAG, "OneCore-DEBUG: Using virtual classloader for " + targetActivity);
                    Activity activity = mBase.newActivity(virtualCl, targetActivity, intent);
                    Log.i(TAG, "OneCore-DEBUG: Activity instantiated SUCCESS: " + activity.getClass().getName());
                    return activity;
                } catch (Exception e) {
                    Log.e(TAG, "!!! OneCore-ERROR: FAILED to instantiate target !!!", e);
                }
            }
        }
        
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Log.d(TAG, "OneCore-DEBUG: callActivityOnCreate -> " + activity.getClass().getName());
        String targetActivity = activity.getIntent().getStringExtra("target_activity");
        if (targetActivity != null) {
            String targetPkg = activity.getIntent().getStringExtra("target_package");
            
            // Ensure Application is bound
            VirtualContainer container = VirtualContainer.getInstance();
            if (container.getTargetApplication() == null && targetPkg != null) {
                 Log.i(TAG, "OneCore-DEBUG: Binding Application on-the-fly for [" + targetPkg + "]");
                 container.bindApplication(activity.getApplicationContext(), "android.app.Application", targetPkg);
            }
            
            fixActivityContext(activity);
            
            // Apply theme
            Integer theme = container.getTheme(targetActivity);
            if (theme != null && theme != 0) {
                Log.d(TAG, "OneCore-DEBUG: Theme applied -> " + theme);
                activity.setTheme(theme);
            }
        }
        mBase.callActivityOnCreate(activity, icicle);
        Log.d(TAG, "OneCore-DEBUG: callActivityOnCreate success.");
    }

    private void fixActivityContext(Activity activity) {
        try {
            String targetPkg = activity.getIntent().getStringExtra("target_package");
            if (targetPkg == null) return;

            Log.i(TAG, "OneCore-DEBUG: Resources switched for context fixing.");
            
            // Use specialized ContextFixer
            OneCoreContextFixer.fixContext(activity, targetPkg);

            Log.i(TAG, "OneCore-DEBUG: setContentView ready for triggering.");
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: Context fix FAILED !!!", e);
        }
    }
}
