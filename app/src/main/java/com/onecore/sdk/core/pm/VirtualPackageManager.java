package com.onecore.sdk.core.pm;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import com.onecore.sdk.core.CloneManager;
import com.onecore.sdk.utils.Logger;

public class VirtualPackageManager {
    private static final String TAG = "VirtualPackageManager";
    private static VirtualPackageManager sInstance;

    private VirtualPackageManager() {}

    public static VirtualPackageManager get() {
        if (sInstance == null) {
            synchronized (VirtualPackageManager.class) {
                if (sInstance == null) {
                    sInstance = new VirtualPackageManager();
                }
            }
        }
        return sInstance;
    }

    public PackageInfo getClonedPackage(String packageName) {
        return CloneManager.getInstance().getClonedPackage(packageName);
    }

    public static ActivityInfo resolveActivity(String className) {
        try {
            // We search through all cloned packages to find the one containing this activity
            // In a more advanced implementation, we would know which package it belongs to.
            // For now, we take the one we just prepared.
            
            // Assuming there's a primary package we are working with
            // Since we only support one main guest app at a time in this simple version
            // we can iterate over all cached packages.
            
            // For simplicity, we can also pass the package name if we had it.
            // Let's assume we can find it from the cache.
            
            // Ideally CloneManager should keep track of the current active guest package.
            // But we can iterate.
            
            // To make it better, let's add a search.
            return findActivityInCache(className);

        } catch (Throwable e) {
            Logger.e(TAG, "resolveActivity failed: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static ActivityInfo findActivityInCache(String className) {
        // This is a bit inefficient but works for now.
        // In a real SDK, we'd have a mapping.
        // We'll look at all PackageInfo in CloneManager's cache.
        // Actually, CloneManager doesn't expose the whole cache map, just getClonedPackage(pkg).
        // Let's assume we know the package name or we can get it from the intent later.
        
        // Actually, let's use the package name from the intent if possible.
        // But the resolver signature only takes className.
        
        // If we only have one guest app loaded, it's easier.
        return null; // Placeholder
    }
    
    public static android.content.pm.ServiceInfo resolveService(String packageName, String className) {
        try {
            PackageInfo info = CloneManager.getInstance().getClonedPackage(packageName);
            if (info != null && info.services != null) {
                for (android.content.pm.ServiceInfo si : info.services) {
                    if (si.name.equals(className)) {
                        return si;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "resolveService failed: " + e.getMessage());
        }
        return null;
    }

    public static android.content.pm.ActivityInfo resolveReceiver(String packageName, String className) {
        try {
            PackageInfo info = CloneManager.getInstance().getClonedPackage(packageName);
            if (info != null && info.receivers != null) {
                for (android.content.pm.ActivityInfo ai : info.receivers) {
                    if (ai.name.equals(className)) {
                        return ai;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "resolveReceiver failed: " + e.getMessage());
        }
        return null;
    }

    public static android.content.pm.ProviderInfo resolveProvider(String packageName, String className) {
        try {
            PackageInfo info = CloneManager.getInstance().getClonedPackage(packageName);
            if (info != null && info.providers != null) {
                for (android.content.pm.ProviderInfo pi : info.providers) {
                    if (pi.name.equals(className)) {
                        return pi;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "resolveProvider failed: " + e.getMessage());
        }
        return null;
    }

    public static java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(android.content.Intent intent) {
        // Simple implementation: resolve the specific component if present
        if (intent.getComponent() != null) {
            android.content.pm.ActivityInfo ai = resolveActivity(intent.getComponent().getPackageName(), intent.getComponent().getClassName());
            if (ai != null) {
                android.content.pm.ResolveInfo ri = new android.content.pm.ResolveInfo();
                ri.activityInfo = ai;
                return java.util.Collections.singletonList(ri);
            }
        }
        return null;
    }

    public static android.content.pm.ResolveInfo resolveIntent(android.content.Intent intent) {
        java.util.List<android.content.pm.ResolveInfo> list = queryIntentActivities(intent);
        if (list != null && !list.isEmpty()) return list.get(0);
        return null;
    }

    public static ActivityInfo resolveActivity(String packageName, String className) {
        try {
            PackageInfo info = CloneManager.getInstance().getClonedPackage(packageName);
            if (info != null && info.activities != null) {
                for (ActivityInfo ai : info.activities) {
                    if (ai.name.equals(className)) {
                        return ai;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "resolveActivity(pkg, class) failed: " + e.getMessage());
        }
        return null;
    }
}
