package com.onecore.sdk.core;

import android.content.Context;
import android.content.res.Resources;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;

/**
 * Manages the creation and consistency of Virtual Contexts.
 * Ensures Activity, Application, and Resources use the same environment.
 */
public class ContextManager {
    private static final String TAG = "OneCore-CtxMgr";

    public static Context createVirtualContext(Context hostContext, String packageName, ClassLoader classLoader, Resources resources) {
        try {
            // In a full implementation, we might create a ContextWrapper or patch a new ContextImpl
            // Here we use the host context as base and fix it to look like the guest
            ContextFixer.fix(hostContext, packageName, classLoader, resources);
            return hostContext;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to create virtual context", e);
            return hostContext;
        }
    }

    /**
     * Ensures an object (like Activity or Service) is correctly bound to the virtual environment.
     */
    public static void bindContext(Object target, String packageName, ClassLoader classLoader, Resources resources) {
        if (target instanceof Context) {
            ContextFixer.fix((Context) target, packageName, classLoader, resources);
        }
    }
}
