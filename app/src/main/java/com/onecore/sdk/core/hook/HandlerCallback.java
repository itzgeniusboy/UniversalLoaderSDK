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
        try {
            Method getCallbacks = transaction.getClass().getDeclaredMethod("getCallbacks");
            getCallbacks.setAccessible(true);
            List callbacks = (List) getCallbacks.invoke(transaction);

            if (callbacks != null) {
                for (Object item : callbacks) {
                    if (item.getClass().getName().contains("LaunchActivityItem")) {
                        processLaunchItem(item);
                    }
                }
            }
        } catch (Exception e) {
            // Android 9/10/11+ often use field access for mActivityCallbacks
            try {
                Field callbacksField = transaction.getClass().getDeclaredField("mActivityCallbacks");
                callbacksField.setAccessible(true);
                List callbacks = (List) callbacksField.get(transaction);
                if (callbacks != null) {
                    for (Object item : callbacks) {
                        if (item.getClass().getName().contains("LaunchActivityItem")) {
                            processLaunchItem(item);
                        }
                    }
                }
            } catch (Exception ex) {
                Logger.e(TAG, "Modern Transaction Interception Failed", ex);
            }
        }
    }

    private void processLaunchItem(Object item) {
        try {
            Field intentField = item.getClass().getDeclaredField("mIntent");
            intentField.setAccessible(true);
            Intent stubIntent = (Intent) intentField.get(item);

            Intent target = stubIntent.getParcelableExtra("EXTRA_TARGET_INTENT");
            if (target != null) {
                intentField.set(item, target);

                Field infoField = item.getClass().getDeclaredField("mInfo");
                infoField.setAccessible(true);
                android.content.pm.ActivityInfo ai = (android.content.pm.ActivityInfo) infoField.get(item);
                fixActivityInfo(ai, target);
                Logger.i(TAG, "Successfully restored Intent for transaction launch");
            }
        } catch (Exception e) {
            Logger.e(TAG, "LaunchItem Restoration Failed", e);
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
