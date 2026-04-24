package com.onecore.sdk.core.hook;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import com.onecore.sdk.core.pm.VirtualPackageManager;
import com.onecore.sdk.utils.Logger;

/**
 * Intercepts LAUNCH_ACTIVITY and EXECUTE_TRANSACTION messages.
 * This is the final opportunity to swap the StubActivity back to the Real Activity.
 */
public class HandlerCallback implements Handler.Callback {
    private static final String TAG = "OneCore-H";

    private static final int LAUNCH_ACTIVITY = 100; // Legacy (< Android 9)
    private static final int EXECUTE_TRANSACTION = 159; // Modern (Android 9+)

    public HandlerCallback(Handler base) {
        // Base is currently unused but can be useful for fallback
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == LAUNCH_ACTIVITY) {
            handleLaunchActivity(msg.obj);
        } else if (msg.what == EXECUTE_TRANSACTION) {
            handleTransaction(msg.obj);
        }
        return false; // Let original Handler continue processing
    }

    private void handleLaunchActivity(Object r) {
        try {
            Field intentField = r.getClass().getDeclaredField("intent");
            intentField.setAccessible(true);
            Intent stubIntent = (Intent) intentField.get(r);
            
            Intent target = stubIntent.getParcelableExtra("_VA_TARGET_");
            if (target != null) {
                intentField.set(r, target);
                
                Field infoField = r.getClass().getDeclaredField("activityInfo");
                infoField.setAccessible(true);
                android.content.pm.ActivityInfo ai = (android.content.pm.ActivityInfo) infoField.get(r);
                fixActivityInfo(ai, target);
            }
        } catch (Exception e) {
            Logger.e(TAG, "handleLaunchActivity Error", e);
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
            // Fallback for fields
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
                Logger.e(TAG, "handleTransaction Error", ex);
            }
        }
    }

    private void processLaunchItem(Object item) {
        try {
            Field intentField = item.getClass().getDeclaredField("mIntent");
            intentField.setAccessible(true);
            Intent stubIntent = (Intent) intentField.get(item);

            Intent target = stubIntent.getParcelableExtra("_VA_TARGET_");
            if (target != null) {
                intentField.set(item, target);

                Field infoField = item.getClass().getDeclaredField("mInfo");
                infoField.setAccessible(true);
                android.content.pm.ActivityInfo ai = (android.content.pm.ActivityInfo) infoField.get(item);
                fixActivityInfo(ai, target);
            }
        } catch (Exception e) {
            Logger.e(TAG, "processLaunchItem Error", e);
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
            Logger.i(TAG, "In-place fix for ActivityInfo complete: " + info.name);
        }
    }
}
