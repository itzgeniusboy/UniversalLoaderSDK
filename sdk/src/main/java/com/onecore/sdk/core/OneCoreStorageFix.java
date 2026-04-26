package com.onecore.sdk.core;

import android.content.Context;
import android.util.Log;
import com.onecore.sdk.utils.ReflectionHelper;
import java.io.File;

/**
 * Advanced, version-adaptive storage sandboxing for virtual environments.
 */
public class OneCoreStorageFix {
    private static final String TAG = "OneCore-StorageFix";

    public static void fix(Context context, String packageName) {
        SafeExecutionManager.run("Storage Path Fix", () -> {
            // Find ContextImpl
            Context baseContext = context;
            while (baseContext instanceof android.content.ContextWrapper) {
                baseContext = ((android.content.ContextWrapper) baseContext).getBaseContext();
            }

            final Object contextImpl = baseContext;
            File dataDir = context.getDir("v_data_" + packageName, Context.MODE_PRIVATE);

            // 1. Redirect SharedPreferences
            File prefsDir = new File(dataDir, "shared_prefs");
            if (!prefsDir.exists()) prefsDir.mkdirs();
            ReflectionHelper.setFieldValue(contextImpl, prefsDir, "mPreferencesDir", "mSharedPrefsDir");

            // 2. Redirect Databases
            File databasesDir = new File(dataDir, "databases");
            if (!databasesDir.exists()) databasesDir.mkdirs();
            // Database path is usually calculated from dataDir, but some internal caches might use fields.
            
            // 3. Handle Scoped Storage markers
            // On Android 11+, apps are restricted. We ensure the package name matches the virtual one.
            ReflectionHelper.setFieldValue(contextImpl, packageName, "mPackageName", "mBasePackageName");
            
            Log.d(TAG, "OneCore-DEBUG: Storage sandbox active at " + dataDir.getAbsolutePath());
        });
    }
}
