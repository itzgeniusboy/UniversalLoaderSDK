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
            // Apply deep patches to ensure the context looks like the guest app
            ContextFixer.fix(hostContext, packageName, classLoader, resources);
            return hostContext;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to create virtual context for " + packageName, e);
            return hostContext;
        }
    }

    /**
     * Ensures an object (like Activity or Service) is correctly bound to the virtual environment.
     */
    public static void bindContext(Object target, String packageName, ClassLoader classLoader, Resources resources) {
        if (target instanceof Context) {
            ContextFixer.fix((Context) target, packageName, classLoader, resources);
        } else if (target != null) {
            // If it's not a context but has a getBaseContext() method (like Activity)
            try {
                java.lang.reflect.Method getBaseContext = target.getClass().getMethod("getBaseContext");
                Context base = (Context) getBaseContext.invoke(target);
                if (base != null) {
                    ContextFixer.fix(base, packageName, classLoader, resources);
                }
            } catch (Exception ignored) {}
        }
    }
}
