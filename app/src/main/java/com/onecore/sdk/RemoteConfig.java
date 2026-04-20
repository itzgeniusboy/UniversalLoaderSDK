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
        if (!SDKLicense.getInstance().isLicensed()) return;
        new Thread(() -> {
            try {
                URL url = new URL("https://api.onecore.sdk/config?sdk_version=1.0.4");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    Scanner scanner = new Scanner(in).useDelimiter("\\A");
                    String response = scanner.hasNext() ? scanner.next() : "";
                    
                    // Simplified parsing for demo. Real app would use JSONObject.
                    parseConfig(response);
                    lastFetch = System.currentTimeMillis();
                    Logger.d(TAG, "Config synced.");
                }
                conn.disconnect();
            } catch (Exception e) {
                Logger.e(TAG, "Config fetch failed", e);
            }
        }).start();
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
