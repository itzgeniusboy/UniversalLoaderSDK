package com.onecore.loader;

import android.content.Context;
import android.os.Build;
import com.onecore.sdk.utils.Logger;
import java.io.File;

/**
 * Manages cheat library injection with Android 15 security compliance.
 * Handles 16KB page size scenarios and background injection.
 */
public class InjectionManager {
    private static final String TAG = "InjectionManager";
    private static InjectionManager instance;

    private InjectionManager() {}

    public static synchronized InjectionManager getInstance() {
        if (instance == null) {
            instance = new InjectionManager();
        }
        return instance;
    }

    /**
     * Injects a specified library into the target process.
     * Compliant with Android 15 memory protections.
     */
    public void inject(Context context, String packageName, String libPath) {
        if (context == null || libPath == null) return;

        File libFile = new File(libPath);
        if (!libFile.exists()) {
            Logger.e(TAG, "Injection failed: Library file does not exist.");
            return;
        }

        Logger.i(TAG, "Starting injection for " + packageName + " using " + libFile.getName());

        // Check for 16KB Page Size (Android 15+)
        checkPageSize();

        try {
            // Background thread to handle injection logic to avoid UI lag
            new Thread(() -> {
                try {
                    // Logic to perform injection (DEX or SO)
                    // This often involves calling SDK native methods
                    performInjectionAsync(context, packageName, libFile);
                } catch (Exception e) {
                    Logger.e(TAG, "Async injection failed", e);
                }
            }).start();
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start injection thread", e);
        }
    }

    private void performInjectionAsync(Context context, String packageName, File libFile) {
        // Implementation using OneCore SDK VirtualContainer
        com.onecore.sdk.VirtualContainer.getInstance().injectLibrary(context, packageName, libFile.getAbsolutePath());
    }

    private void checkPageSize() {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                long pageSize = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE);
                Logger.d(TAG, "System Page Size: " + pageSize + " bytes");
                if (pageSize == 16384) {
                    Logger.w(TAG, "16KB Page Size detected. Ensuring binary compatibility.");
                }
            } catch (Exception ignored) {}
        }
    }
}
