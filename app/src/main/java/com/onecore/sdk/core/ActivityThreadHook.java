package com.onecore.sdk.core;

import android.os.Handler;
import com.onecore.sdk.core.hook.HandlerCallback;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Handles the actual injection of Hooks into the Android ActivityThread.
 */
public class ActivityThreadHook {
    private static final String TAG = "OneCore-AT-Hook";

    public static void inject() {
        try {
            Logger.i(TAG, "Locating ActivityThread...");
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method curAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            curAtMethod.setAccessible(true);
            Object activityThread = curAtMethod.invoke(null);

            if (activityThread == null) {
                Logger.e(TAG, "ActivityThread is NULL. Host process corrupted?");
                return;
            }

            // 1. Swap Instrumentation
            Field mInstrField = atClass.getDeclaredField("mInstrumentation");
            mInstrField.setAccessible(true);
            android.app.Instrumentation baseInst = (android.app.Instrumentation) mInstrField.get(activityThread);
            
            if (!(baseInst instanceof CustomInstrumentation)) {
                mInstrField.set(activityThread, new CustomInstrumentation(baseInst));
                Logger.d(TAG, "Instrumentation Hooked (CustomInstrumentation)");
            }

            // 2. Swap Handler Callback
            Field mHField = atClass.getDeclaredField("mH");
            mHField.setAccessible(true);
            Handler mH = (Handler) mHField.get(activityThread);

            Field mCbField = Handler.class.getDeclaredField("mCallback");
            mCbField.setAccessible(true);
            
            Object currentCb = mCbField.get(mH);
            if (!(currentCb instanceof HandlerCallback)) {
                mCbField.set(mH, new HandlerCallback(mH));
                Logger.d(TAG, "ActivityThread Handler Hooked (HandlerCallback)");
            }

            Logger.i(TAG, ">> ActivityThread Hooks ACTIVE <<");

        } catch (Exception e) {
            Logger.e(TAG, "ActivityThread Injection CRITICAL FAILURE", e);
        }
    }
}
