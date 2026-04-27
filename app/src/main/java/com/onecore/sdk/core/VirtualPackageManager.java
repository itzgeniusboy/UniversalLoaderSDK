package com.onecore.sdk.core;

import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import java.util.HashMap;
import java.util.Map;

/**
 * Powerful Virtual Package Manager.
 * Stores and manages metadata for apps running inside our container.
 * This is the 'Brain' that tells the game what it wants to hear about its package.
 */
public class VirtualPackageManager {
    private static VirtualPackageManager sInstance;
    private final Map<String, PackageInfo> virtualPackages = new HashMap<>();

    public static VirtualPackageManager get() {
        if (sInstance == null) sInstance = new VirtualPackageManager();
        return sInstance;
    }

    public void addVirtualPackage(PackageInfo info) {
        if (info != null) {
            virtualPackages.put(info.packageName, info);
        }
    }

    public PackageInfo getPackageInfo(String packageName, int flags) {
        return virtualPackages.get(packageName);
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags) {
        PackageInfo info = virtualPackages.get(packageName);
        return info != null ? info.applicationInfo : null;
    }

    /**
     * Stealth: Spoofs the package name if needed.
     */
    public String spoofPackageName(String original) {
        // Advanced logic to return a legitimate-looking package name 
        // to tools trying to detect sandbox.
        return original;
    }
}
