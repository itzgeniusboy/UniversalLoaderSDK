package com.onecore.sdk.core.system.pm;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import com.onecore.sdk.core.VirtualPackageManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Server-side implementation of Virtual Package Manager.
 */
public class BPackageManagerService extends IBPackageManagerService.Stub {
    private static final BPackageManagerService sService = new BPackageManagerService();
    private final Map<String, PackageInfo> mInstalledPackages = new HashMap<>();

    public static BPackageManagerService get() {
        return sService;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags, int userId) throws RemoteException {
        return VirtualPackageManager.getInstance().getPackageInfo(packageName, flags);
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) throws RemoteException {
        return VirtualPackageManager.getInstance().getApplicationInfo(packageName, flags);
    }

    @Override
    public int checkPermission(String permissionName, String packageName, int userId) throws RemoteException {
        // Broadly grant permissions in sandbox to avoid system dialogs for target app
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean isPackageInstalled(String packageName, int userId) throws RemoteException {
        return VirtualPackageManager.getInstance().getPackageInfo(packageName, 0) != null;
    }
}
