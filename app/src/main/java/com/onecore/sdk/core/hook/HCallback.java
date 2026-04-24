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
            fixIntent(stubIntent);

            // Fix ActivityInfo
            Field infoField = r.getClass().getDeclaredField("activityInfo");
            infoField.setAccessible(true);
            android.content.pm.ActivityInfo info = (android.content.pm.ActivityInfo) infoField.get(r);
            fixActivityInfo(info, stubIntent);

        } catch (Throwable e) {
            Logger.e(TAG, "handleLaunchActivity FAILED: " + e.getMessage());
        }
    }

    private void handleTransaction(Object transaction) {
        try {
            Field callbacksField = transaction.getClass().getDeclaredField("mActivityCallbacks");
            callbacksField.setAccessible(true);

            List callbacks = (List) callbacksField.get(transaction);
            if (callbacks == null) return;

            for (Object item : callbacks) {
                if (item.getClass().getName().contains("LaunchActivityItem")) {
                    Field intentField = item.getClass().getDeclaredField("mIntent");
                    intentField.setAccessible(true);
                    Intent stubIntent = (Intent) intentField.get(item);
                    fixIntent(stubIntent);

                    // Fix ActivityInfo
                    Field infoField = item.getClass().getDeclaredField("mInfo");
                    infoField.setAccessible(true);
                    android.content.pm.ActivityInfo info = (android.content.pm.ActivityInfo) infoField.get(item);
                    fixActivityInfo(info, stubIntent);
                }
            }

        } catch (Throwable e) {
            Logger.e(TAG, "handleTransaction FAILED: " + e.getMessage());
        }
    }

    private void fixIntent(Intent stubIntent) {
        String target = stubIntent.getStringExtra("target_activity");
        if (target != null) {
            stubIntent.setClassName(
                    CloneManager.getInstance().getHostContext().getPackageName(),
                    target
            );
            Logger.i("VA", "HCallback fixed intent → " + target);
        }
    }

    private void fixActivityInfo(android.content.pm.ActivityInfo info, Intent stubIntent) {
        if (info == null) return;
        String targetActivity = stubIntent.getStringExtra("target_activity");
        String targetPackage = stubIntent.getStringExtra("target_package");
        
        if (targetActivity != null && targetPackage != null) {
            android.content.pm.PackageInfo pkgInfo = CloneManager.getInstance().getClonedPackage(targetPackage);
            if (pkgInfo != null && pkgInfo.activities != null) {
                for (android.content.pm.ActivityInfo ai : pkgInfo.activities) {
                    if (targetActivity.equals(ai.name)) {
                        // Copy properties from real ActivityInfo to the stub's ActivityInfo
                        // This preserves themes, launch modes, etc.
                        info.name = ai.name;
                        info.packageName = ai.packageName;
                        info.theme = ai.theme;
                        info.launchMode = ai.launchMode;
                        info.applicationInfo = ai.applicationInfo;
                        info.flags = ai.flags;
                        info.screenOrientation = ai.screenOrientation;
                        Logger.i(TAG, "Fixed ActivityInfo for " + targetActivity);
                        break;
                    }
                }
            }
        }
    }
}
