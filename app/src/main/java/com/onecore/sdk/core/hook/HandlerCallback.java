package com.onecore.sdk.core.hook;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import com.onecore.sdk.core.pm.VirtualPackageManager;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * FINAL Handler Callback for ActivityThread.mH.
 * Restores original Intent in both LaunchActivityItem and ActivityClientRecord.
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
        if (r == null) return;
        try {
            Field intentField = getField(r.getClass(), "intent");
            if (intentField != null) {
                Intent stubIntent = (Intent) intentField.get(r);
                if (stubIntent != null) {
                    Intent target = stubIntent.getParcelableExtra("EXTRA_TARGET_INTENT");
                    if (target != null) {
                        intentField.set(r, target);
                        
                        Field infoField = getField(r.getClass(), "activityInfo");
                        if (infoField != null) {
                            android.content.pm.ActivityInfo ai = (android.content.pm.ActivityInfo) infoField.get(r);
                            fixActivityInfo(ai, target);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Legacy Launch Interception Failed", e);
        }
    }

    private void handleTransaction(Object transaction) {
        if (transaction == null) return;
        try {
            // 1. Extract Token from ClientTransaction
            android.os.IBinder token = null;
            try {
                Field tokenField = getField(transaction.getClass(), "mActivityToken");
                if (tokenField != null) {
                    token = (android.os.IBinder) tokenField.get(transaction);
                }
            } catch (Exception ignored) {}

            // 2. Extract Callbacks
            List<?> callbacks = null;
            try {
                Method getCallbacks = transaction.getClass().getDeclaredMethod("getCallbacks");
                getCallbacks.setAccessible(true);
                callbacks = (List<?>) getCallbacks.invoke(transaction);
            } catch (Exception e) {
                Field callbacksField = getField(transaction.getClass(), "mActivityCallbacks");
                if (callbacksField != null) {
                    callbacks = (List<?>) callbacksField.get(transaction);
                }
            }

            if (callbacks != null) {
                for (Object item : callbacks) {
                    if (item != null && item.getClass().getName().contains("LaunchActivityItem")) {
                        // 3. Fix Intent in LaunchActivityItem
                        Intent target = processLaunchItem(item);
                        
                        // 4. Fix Intent in ActivityClientRecord (mActivities)
                        if (target != null && token != null) {
                            fixActivityClientRecord(token, target);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Transaction Interception CRITICAL FAILURE", e);
        }
    }

    private Intent processLaunchItem(Object item) {
        try {
            Field intentField = getField(item.getClass(), "mIntent");
            if (intentField == null) {
                intentField = getField(item.getClass(), "intent");
            }

            if (intentField != null) {
                Intent stubIntent = (Intent) intentField.get(item);
                if (stubIntent != null) {
                    Intent target = stubIntent.getParcelableExtra("EXTRA_TARGET_INTENT");
                    if (target != null) {
                        intentField.set(item, target);

                        Field infoField = getField(item.getClass(), "mInfo");
                        if (infoField == null) {
                            infoField = getField(item.getClass(), "info");
                        }
                        
                        if (infoField != null) {
                            android.content.pm.ActivityInfo ai = (android.content.pm.ActivityInfo) infoField.get(item);
                            fixActivityInfo(ai, target);
                        }
                        
                        Logger.i(TAG, "Restored Transaction Intent: " + target.getComponent().getClassName());
                        return target;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "processLaunchItem FAILED", e);
        }
        return null;
    }

    private void fixActivityClientRecord(android.os.IBinder token, Intent target) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method curAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            curAtMethod.setAccessible(true);
            Object activityThread = curAtMethod.invoke(null);

            Field mActivitiesField = getField(atClass, "mActivities");
            if (mActivitiesField != null) {
                Map<?, ?> mActivities = (Map<?, ?>) mActivitiesField.get(activityThread);
                Object record = mActivities.get(token);
                if (record != null) {
                    Field intentField = getField(record.getClass(), "intent");
                    if (intentField != null) {
                        intentField.set(record, target);
                        Logger.d(TAG, "ActivityClientRecord intent updated for token: " + token);
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "fixActivityClientRecord FAILED", e);
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
        }
    }

    private Field getField(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
