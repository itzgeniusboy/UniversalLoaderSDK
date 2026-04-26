package com.onecore.sdk.core;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
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
    private static final int BIND_APPLICATION = 110;

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
            } else if (msg.what == BIND_APPLICATION) {
                handleBindApplication(msg.obj);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in HCallback: " + msg.what, t);
        }
        
        return false; // Return false to let mH handle it normally after our interception
    }

    private void handleBindApplication(Object data) {
        if (data == null) return;
        SafeExecutionManager.run("H-Handler BindApp", () -> {
            try {
                android.content.pm.ApplicationInfo ai = (android.content.pm.ApplicationInfo) ReflectionHelper.getFieldValue(data, "appInfo");
                String targetPkg = VirtualContainer.getInstance().getPackageName();
                
                if (ai != null && targetPkg != null && ai.packageName.equals(targetPkg)) {
                    Log.i(TAG, ">>> H-Handler BIND_APPLICATION for virtual: " + targetPkg);
                    
                    // Replace LoadedApk in AppBindData
                    Object loadedApk = OneCoreLoadedApkManager.getLoadedApk(
                        com.onecore.sdk.OneCoreSDK.getContext(),
                        VirtualContainer.getInstance().getApkPath(),
                        targetPkg,
                        VirtualContainer.getInstance().getClassLoader(),
                        VirtualContainer.getInstance().getResources()
                    );
                    
                    if (loadedApk != null) {
                        ReflectionHelper.setFieldValue(data, loadedApk, "info");
                        Log.d(TAG, "LoadedApk swapped in AppBindData successfully.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to patch BindApplication", e);
            }
        });
    }

    private void handleTransaction(Object transaction) {
        if (transaction == null) return;
        
        try {
            // Android 9+ uses ClientTransaction
            // mActivityCallbacks contains a list of ClientTransactionItem
            List callbacks = (List) ReflectionHelper.getFieldValue(transaction, "mActivityCallbacks", "mCallbacks");
            if (callbacks != null) {
                for (Object item : callbacks) {
                    String className = item.getClass().getName();
                    // Check for LaunchActivityItem or standard ActivityLaunchItem
                    if (className.contains("LaunchActivityItem") || className.contains("ActivityLaunchItem")) {
                        Intent intent = (Intent) ReflectionHelper.getFieldValue(item, "mIntent");
                        redirectIntent(intent, item);
                    }
                }
            }
            
            // Also check LifecycleItem (e.g. ResumeActivityItem)
            Object lifecycleItem = ReflectionHelper.getFieldValue(transaction, "mLifecycleItem");
            if (lifecycleItem != null) {
                // We might need to adjust based on state if it uses tokens we've swapped
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
                VirtualContainer container = VirtualContainer.getInstance();
                if (container.getClassLoader() == null) {
                    try {
                        Object at = ReflectionHelper.invokeMethod("android.app.ActivityThread", "currentActivityThread");
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
