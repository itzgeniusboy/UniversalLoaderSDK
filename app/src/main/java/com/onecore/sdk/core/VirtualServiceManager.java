package com.onecore.sdk.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Root Registry for all OneCore Virtual Services.
 * Mirrors the Android System Service Manager.
 */
public class VirtualServiceManager {
    private static final Map<String, Object> SERVICES = new HashMap<>();

    static {
        SERVICES.put("package", VirtualPackageManager.get());
        SERVICES.put("activity", VirtualActivityManager.get());
    }

    public static Object getService(String name) {
        return SERVICES.get(name);
    }

    public static VirtualPackageManager getPackageService() {
        return (VirtualPackageManager) getService("package");
    }

    public static VirtualActivityManager getActivityService() {
        return (VirtualActivityManager) getService("activity");
    }
}
