package com.onecore.sdk.core.hook;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import com.onecore.sdk.core.pm.VirtualPackageManager;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * FINAL Handler Callback for ActivityThread.mH.
 * Restores original Intent right before Activity creation.
 */
public class HandlerCallback implements Handler.Callback {
    private static final String TAG = "OneCore-Handler";

    private static final int LAUNCH_ACTIVITY = 100;
    private static final int EXECUTE_TRANSACTION = 159;

    public HandlerCallback(Handler base) {}

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == LAUNCH_ACTIVITY) {
            handleLaunchActivity(msg.obj);
        } else if (msg.what == EXECUTE_TRANSACTION) {
            handleTransaction(msg.obj);
        }
        return false;
    }

    private void handleLaunchActivity(Object r) {
        try {
            Field intentField = r.getClass().getDeclaredField("intent");
            intentField.setAccessible(true);
            Intent stubIntent = (Intent) intentField.get(r);
            
            Intent target = stubIntent.getParcelableExtra("EXTRA_TARGET_INTENT");
            if (target != null) {
                intentField.set(r, target);
                
                // Fix ActivityInfo metadata
                Field infoField = r.getClass().getDeclaredField("activityInfo");
                infoField.setAccessible(true);
                android.content.pm.ActivityInfo ai = (android.content.pm.ActivityInfo) infoField.get(r);
                fixActivityInfo(ai, target);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Legacy Launch Interception Failed", e);
        }
    }

    private void handleTransaction(Object transaction) {
        if (transaction == null) return;
        try {
            // ClientTransaction.getCallbacks()
            List<?> callbacks = null;
            try {
                Method getCallbacks = transaction.getClass().getDeclaredMethod("getCallbacks");
                getCallbacks.setAccessible(true);
                callbacks = (List<?>) getCallbacks.invoke(transaction);
            } catch (Exception e) {
                Field callbacksField = transaction.getClass().getDeclaredField("mActivityCallbacks");
                callbacksField.setAccessible(true);
                callbacks = (List<?>) callbacksField.get(transaction);
            }

            if (callbacks != null) {
                for (Object item : callbacks) {
                    if (item != null && item.getClass().getName().contains("LaunchActivityItem")) {
                        processLaunchItem(item);
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "handleTransaction CRITICAL FAILURE", e);
        }
    }

    private void processLaunchItem(Object item) {
        try {
            // LaunchActivityItem.mIntent
            Field intentField = item.getClass().getDeclaredField("mIntent");
            intentField.setAccessible(true);
            Intent stubIntent = (Intent) intentField.get(item);

            if (stubIntent != null) {
                Intent target = stubIntent.getParcelableExtra("EXTRA_TARGET_INTENT");
                if (target != null) {
                    intentField.set(item, target);

                    // LaunchActivityItem.mInfo (ActivityInfo)
                    Field infoField = item.getClass().getDeclaredField("mInfo");
                    infoField.setAccessible(true);
                    android.content.pm.ActivityInfo ai = (android.content.pm.ActivityInfo) infoField.get(item);
                    fixActivityInfo(ai, target);
                    
                    Logger.i(TAG, "Restored Transaction Intent: " + target.getComponent().getClassName());
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "processLaunchItem FAILED", e);
        }
    }

    private void fixActivityInfo(android.content.pm.ActivityInfo info, Intent target) {
        if (info == null || target.getComponent() == null) return;
        
        String pkg = target.getComponent().getPackageName();
        String cls = target.getComponent().getClassName();
        
        android.content.pm.ActivityInfo realAi = VirtualPackageManager.resolveActivity(pkg, cls);
        if (realAi != null) {
            info.name = realAi.name;
            info.packageName = pkg;
            info.theme = realAi.theme;
            info.applicationInfo = realAi.applicationInfo;
            // Transfer other important metadata here
        }
    }
}
