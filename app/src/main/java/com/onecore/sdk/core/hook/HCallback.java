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
            String targetActivity = stubIntent.getStringExtra("target_activity");
            String targetPackage = stubIntent.getStringExtra("target_package");

            if (targetActivity != null) {
                // ✅ Fix Intent
                stubIntent.setClassName(
                        CloneManager.getInstance().getHostContext().getPackageName(),
                        targetActivity
                );

                // 🔥 Inject ActivityInfo
                Field activityInfoField = r.getClass().getDeclaredField("activityInfo");
                activityInfoField.setAccessible(true);

                android.content.pm.ActivityInfo ai = com.onecore.sdk.core.pm.VirtualPackageManager.resolveActivity(targetPackage, targetActivity);

                if (ai != null) {
                    // Update metadata to match host so system handles it
                    String hostPkg = CloneManager.getInstance().getHostContext().getPackageName();
                    ai.packageName = hostPkg;
                    if (ai.applicationInfo != null) {
                        ai.applicationInfo.packageName = hostPkg;
                    }
                    
                    activityInfoField.set(r, ai);
                    Logger.i("VA", "Injected ActivityInfo → " + targetActivity);
                }
            }

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
                    String targetActivity = stubIntent.getStringExtra("target_activity");
                    String targetPackage = stubIntent.getStringExtra("target_package");

                    if (targetActivity != null) {
                        // Fix Intent
                        stubIntent.setClassName(
                                CloneManager.getInstance().getHostContext().getPackageName(),
                                targetActivity
                        );

                        // Fix ActivityInfo
                        Field aiField = item.getClass().getDeclaredField("mInfo");
                        aiField.setAccessible(true);

                        android.content.pm.ActivityInfo ai = com.onecore.sdk.core.pm.VirtualPackageManager.resolveActivity(targetPackage, targetActivity);

                        if (ai != null) {
                            String hostPkg = CloneManager.getInstance().getHostContext().getPackageName();
                            ai.packageName = hostPkg;
                            if (ai.applicationInfo != null) {
                                ai.applicationInfo.packageName = hostPkg;
                            }
                            
                            aiField.set(item, ai);
                            Logger.i("VA", "Transaction ActivityInfo injected → " + targetActivity);
                        }
                    }
                }
            }

        } catch (Throwable e) {
            Logger.e(TAG, "handleTransaction FAILED: " + e.getMessage());
        }
    }
}
