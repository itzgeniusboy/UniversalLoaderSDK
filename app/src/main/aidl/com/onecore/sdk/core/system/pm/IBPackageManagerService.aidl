package com.onecore.sdk.core.system.pm;

import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;

interface IBPackageManagerService {
    PackageInfo getPackageInfo(String packageName, int flags, int userId);
    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);
    int checkPermission(String permissionName, String packageName, int userId);
    boolean isPackageInstalled(String packageName, int userId);
}
