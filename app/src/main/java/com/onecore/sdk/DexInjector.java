package com.onecore.sdk;

import android.content.Context;
import com.onecore.sdk.utils.Logger;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.lang.reflect.Method;

/**
 * Dex Injection Engine for OneCore SDK (Non-root, Pure Java).
 * Loads and executes code from external .dex or .jar files.
 */
public class DexInjector {
    private static final String TAG = "DexInjector";

    /**
     * Injects and executes a DEX file into the current process context.
     * @param context Application context.
     * @param dexPath Path to the .dex or .jar file.
     * @param entryClass Fully qualified class name to load (e.g., com.cheat.Main).
     * @param entryMethod Static method name to execute (e.g., init).
     */
    public static void injectDex(Context context, String dexPath, String entryClass, String entryMethod) {
        try {
            File dexFile = new File(dexPath);
            if (!dexFile.exists()) {
                Logger.e(TAG, "DEX file not found: " + dexPath);
                return;
            }

            // Optimized dex output directory
            File optDir = context.getCodeCacheDir();
            
            Logger.d(TAG, "Injecting DEX: " + dexPath + " -> " + entryClass);

            DexClassLoader classLoader = new DexClassLoader(
                dexPath,
                optDir.getAbsolutePath(),
                null,
                context.getClassLoader()
            );

            Class<?> clazz = classLoader.loadClass(entryClass);
            Method method = clazz.getDeclaredMethod(entryMethod, Context.class);
            method.setAccessible(true);
            
            // Execute the entry point
            method.invoke(null, context);
            
            Logger.i(TAG, "Successfully injected and executed class: " + entryClass);

        } catch (Exception e) {
            Logger.e(TAG, "DEX Injection Failed", e);
        }
    }
}
