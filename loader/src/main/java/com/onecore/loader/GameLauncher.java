package com.onecore.loader;

import android.content.Context;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;

/**
 * Handles the final sequence of Injection -> Cloned Launch.
 */
public class GameLauncher {
    private static final String TARGET_PKG = "com.pubg.imobile";

    public static void start(Context context) {
        Logger.i("GameLauncher", "Initiating Secure Launch...");
        // Launch via Virtual Container which handles the SandboxActivity trigger
        VirtualContainer.getInstance().launch(context, TARGET_PKG);
    }
}
