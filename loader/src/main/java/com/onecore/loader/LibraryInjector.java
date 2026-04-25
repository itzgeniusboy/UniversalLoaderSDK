package com.onecore.loader;

import android.content.Context;
import android.util.Log;

/**
 * Simplified Injector for minimal working version.
 */
public class LibraryInjector {
    public static void inject(Context context, String packageName, String customPath) {
        Log.i("Injector", "Injection skipped in minimal version.");
    }
}
