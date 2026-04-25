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
     * Adaptive fallback for different signatures if needed.
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        
        Log.d(TAG, "OneCore-DEBUG: execStartActivity intercepted. Target=" + target);
        
        // Rewrite intent for StubActivity
        intent = OneCoreStubManager.replaceWithStub(intent, who.getPackageName());

        final Intent finalIntent = intent;
        return SafeExecutionManager.runWithResult("execStartActivity", () -> {
            Method execMethod = ReflectionHelper.findMethod(Instrumentation.class, "execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class);
            
            if (execMethod != null) {
                return (ActivityResult) execMethod.invoke(mBase, who, contextThread, token, target, finalIntent, requestCode, options);
            }
            return null;
        }, null);
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) 
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        
        String targetActivity = intent.getStringExtra("target_activity");
        if (targetActivity != null) {
            return SafeExecutionManager.runWithResult("newActivity (" + targetActivity + ")", () -> {
                ClassLoader virtualCl = VirtualContainer.getInstance().getClassLoader();
                if (virtualCl != null) {
                    Log.d(TAG, "OneCore-DEBUG: Using virtual classloader for " + targetActivity);
                    return mBase.newActivity(virtualCl, targetActivity, intent);
                }
                return mBase.newActivity(cl, targetActivity, intent);
            }, super.newActivity(cl, className, intent));
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
