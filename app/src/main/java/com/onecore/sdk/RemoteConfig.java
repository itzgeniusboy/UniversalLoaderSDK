package com.onecore.sdk;

import com.onecore.sdk.utils.Logger;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Real HTTP-based remote configuration for OneCore SDK Engine.
 */
public class RemoteConfig {
    private static final String TAG = "RemoteConfig";
    private static RemoteConfig instance;
    private final Map<String, String> config = new HashMap<>();
    private long lastFetch = 0;

    private RemoteConfig() {}

    public static synchronized RemoteConfig getInstance() {
        if (instance == null) {
            instance = new RemoteConfig();
        }
        return instance;
    }

    public void fetchRemoteConfig() {
        // No-op: Autonomous mode
        Logger.d(TAG, "Config sync skipped (Offline Mode). Using local defaults.");
        lastFetch = System.currentTimeMillis();
    }

    private void parseConfig(String raw) {
        // Dummy parser: key=value
        for (String line : raw.split("\n")) {
            String[] parts = line.split("=");
            if (parts.length == 2) config.put(parts[0], parts[1]);
        }
    }

    public String getConfigValue(String key) {
        if (!SDKLicense.getInstance().isLicensed()) return null;
        return config.get(key);
    }

    public boolean isFeatureEnabled(String feature) {
        if (!SDKLicense.getInstance().isLicensed()) return false;
        return "true".equals(config.get(feature));
    }

    public void refreshConfig() {
        fetchRemoteConfig();
    }
}
