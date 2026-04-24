package com.onecore.sdk.core.hook;

import android.os.Handler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.onecore.sdk.utils.Logger;

public class ActivityThreadHandlerHook {
    private static final String TAG = "ActivityThreadHandlerHook";

    public static void hook() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");

            Method current = atClass.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);

            Object activityThread = current.invoke(null);

            Field mHField = atClass.getDeclaredField("mH");
            mHField.setAccessible(true);

            Handler mH = (Handler) mHField.get(activityThread);

            Field mCallbackField = Handler.class.getDeclaredField("mCallback");
            mCallbackField.setAccessible(true);

            mCallbackField.set(mH, new HandlerCallback(mH));
            Logger.i(TAG, "ActivityThread.mH.mCallback HOOKED SUCCESS.");

        } catch (Throwable e) {
            Logger.e(TAG, "ActivityThreadHandlerHook FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
