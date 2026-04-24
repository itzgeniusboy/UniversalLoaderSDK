package com.onecore.sdk.core.hook;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.List;
import com.onecore.sdk.core.CloneManager;
import com.onecore.sdk.utils.Logger;

public class HCallback implements Handler.Callback {
    private static final String TAG = "HCallback";

    private static final int LAUNCH_ACTIVITY = 100; // Android < 9
    private static final int EXECUTE_TRANSACTION = 159; // Android 9+

    private final Handler base;

    public HCallback(Handler base) {
        this.base = base;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case LAUNCH_ACTIVITY:
                handleLaunchActivity(msg.obj);
                break;
            case EXECUTE_TRANSACTION:
                handleTransaction(msg.obj);
                break;
        }
        // Return false to allow the original handler to process the message afterwards
        return false;
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

        } catch (Throwable e) {
            Logger.e(TAG, "handleLaunchActivity FAILED: " + e.getMessage());
        }
    }

    private void handleTransaction(Object transaction) {
        try {
            // Android 9+ uses ClientTransaction. We try to get mActivityCallbacks list.
            List callbacks = null;
            try {
                Field callbacksField = transaction.getClass().getDeclaredField("mActivityCallbacks");
                callbacksField.setAccessible(true);
                callbacks = (List) callbacksField.get(transaction);
            } catch (Exception e) {
                // Fallback for different OS versions
                Method getCallbacks = transaction.getClass().getDeclaredMethod("getCallbacks");
                getCallbacks.setAccessible(true);
                callbacks = (List) getCallbacks.invoke(transaction);
            }
 
            if (callbacks != null) {
                for (Object item : callbacks) {
                    if (item.getClass().getName().contains("LaunchActivityItem")) {
                        processLaunchItem(item);
                    }
                }
            }
 
        } catch (Throwable e) {
            Logger.e(TAG, "handleTransaction FAILED: " + e.getMessage());
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
                Logger.d(TAG, "Successfully intercepted LaunchActivityItem");
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to process LaunchActivityItem: " + e.getMessage());
        }
    }

    private void fixActivityInfo(android.content.pm.ActivityInfo info, Intent target) {
        if (info == null || target.getComponent() == null) return;
        
        String targetActivity = target.getComponent().getClassName();
        String targetPackage = target.getComponent().getPackageName();
        
        android.content.pm.ActivityInfo realAi = com.onecore.sdk.core.pm.VirtualPackageManager.resolveActivity(targetPackage, targetActivity);
        if (realAi != null) {
            // Hijack the existing info object to avoid breaking ActivityThread references
            info.name = realAi.name;
            info.packageName = targetPackage; // Use real package to trigger LoadedApk lookup
            info.theme = realAi.theme;
            info.launchMode = realAi.launchMode;
            
            // 🔥 CRITICAL: Fix ApplicationInfo
            if (realAi.applicationInfo != null) {
                info.applicationInfo = new android.content.pm.ApplicationInfo(realAi.applicationInfo);
            }
            
            info.flags = realAi.flags;
            info.screenOrientation = realAi.screenOrientation;
            
            Logger.i("VA", "HCallback fixed ActivityInfo & AppInfo for → " + targetActivity);
        }
    }
}
