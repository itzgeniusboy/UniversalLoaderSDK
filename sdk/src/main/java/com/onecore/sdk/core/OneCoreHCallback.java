package com.onecore.sdk.core;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.onecore.sdk.utils.ReflectionHelper;
import java.util.List;

/**
 * Intercepts ActivityThread H messages to swap Intent/ActivityInfo back to virtual app.
 * Compatible with Android 10-17 ClientTransaction.
 */
public class OneCoreHCallback implements Handler.Callback {
    private static final String TAG = "OneCore-HCallback";
    private final Handler mBase;

    // Message codes
    private static final int EXECUTE_TRANSACTION = 159;
    private static final int LAUNCH_ACTIVITY = 100;

    public OneCoreHCallback(Handler base) {
        this.mBase = base;
    }

    @Override
    public boolean handleMessage(Message msg) {
        try {
            if (msg.what == EXECUTE_TRANSACTION) {
                handleTransaction(msg.obj);
            } else if (msg.what == LAUNCH_ACTIVITY) {
                handleLaunchActivity(msg.obj);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in HCallback", t);
        }
        
        // Let the base handler process the message
        mBase.handleMessage(msg);
        return true; 
    }

    private void handleTransaction(Object transaction) {
        if (transaction == null) return;
        
        try {
            // Android 9-17 uses ClientTransaction
            List callbacks = (List) ReflectionHelper.getFieldValue(transaction, "mActivityCallbacks", "mCallbacks");
            if (callbacks != null) {
                for (Object item : callbacks) {
                    String className = item.getClass().getName();
                    if (className.contains("LaunchActivityItem") || className.contains("ActivityLaunchItem")) {
                        Intent intent = (Intent) ReflectionHelper.getFieldValue(item, "mIntent");
                        redirectIntent(intent, item);
                    }
                }
            }
        } catch (Exception e) {
            // Log.d(TAG, "Not a ClientTransaction or reflection failed");
        }
    }

    private void handleLaunchActivity(Object record) {
        if (record == null) return;
        try {
            Intent intent = (Intent) ReflectionHelper.getFieldValue(record, "intent");
            redirectIntent(intent, record);
        } catch (Exception e) {
            Log.e(TAG, "handleLaunchActivity failed", e);
        }
    }

    private void redirectIntent(Intent intent, Object record) {
        if (intent == null) return;
        
        String targetPkg = intent.getStringExtra("target_package");
        String targetActivity = intent.getStringExtra("target_activity");
        String targetApk = intent.getStringExtra("target_apk_path");
        
        if (targetPkg != null && targetActivity != null) {
            Log.i(TAG, "OneCore-DEBUG: H-Handler redirecting " + targetActivity);
            
            // Dynamic initialization in child process
            if (targetApk != null) {
                com.onecore.sdk.VirtualContainer container = com.onecore.sdk.VirtualContainer.getInstance();
                if (container.getClassLoader() == null) {
                    try {
                        Object at = ReflectionHelper.invokeMethod(null, "currentActivityThread");
                        android.app.Application app = (android.app.Application) ReflectionHelper.getFieldValue(at, "mInitialApplication");
                        if (app != null) {
                            Log.i(TAG, ">>> Performing Dynamic Process Initialization for: " + targetPkg);
                            container.installApk(app, targetApk, targetPkg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Auto-init failed in H-Handler", e);
                    }
                }
            }

            // Swap activity info in the record/item
            android.content.pm.ActivityInfo ai = OneCorePackageManagerProxy.getActivityInfo(
                new android.content.ComponentName(targetPkg, targetActivity));
            
            if (ai != null) {
                try {
                    ReflectionHelper.setFieldValue(record, ai, "mInfo", "activityInfo", "info");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to swap ActivityInfo");
                }
            }
        }
    }
}
