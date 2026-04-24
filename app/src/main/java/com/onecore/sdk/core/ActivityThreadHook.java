package com.onecore.sdk.core;

import android.os.Handler;
import com.onecore.sdk.core.hook.HandlerCallback;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * FINAL ActivityThread Hook.
 * Responsible for core system service redirection and callback injection.
 */
public class ActivityThreadHook {
    private static final String TAG = "OneCore-AT";

    public static void inject() {
        try {
            Logger.i(TAG, "Initiating ActivityThread Injection...");
            
            // 1. Find ActivityThread instance
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            currentAtMethod.setAccessible(true);
            Object activityThread = currentAtMethod.invoke(null);

            if (activityThread == null) {
                Logger.e(TAG, "CRITICAL: ActivityThread not found!");
                return;
            }

            // 2. Redirect Instrumentation
            Field instrField = atClass.getDeclaredField("mInstrumentation");
            instrField.setAccessible(true);
            android.app.Instrumentation baseInst = (android.app.Instrumentation) instrField.get(activityThread);
            
            if (!(baseInst instanceof CustomInstrumentation)) {
                instrField.set(activityThread, new CustomInstrumentation(baseInst));
                Logger.d(TAG, "Instrumentation redirected via CustomInstrumentation");
            }

            // 3. Inject Handler.Callback into mH
            Field hField = atClass.getDeclaredField("mH");
            hField.setAccessible(true);
            Handler mH = (Handler) hField.get(activityThread);

            Field cbField = Handler.class.getDeclaredField("mCallback");
            cbField.setAccessible(true);
            
            Object currentCb = cbField.get(mH);
            if (!(currentCb instanceof HandlerCallback)) {
                cbField.set(mH, new HandlerCallback(mH));
                Logger.d(TAG, "ActivityThread.mH hooked via HandlerCallback");
            }

            Logger.i(TAG, "PHASE 1 Hooks successfully injected.");

        } catch (Exception e) {
            Logger.e(TAG, "ActivityThread Hooked FAILED", e);
        }
    }
}
