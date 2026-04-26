package com.onecore.sdk.core;

import android.content.Intent;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages stub activity mapping.
 */
public class OneCoreStubManager {
    private static final String TAG = "OneCore-StubManager";
    private static final Map<String, String> mStubMap = new HashMap<>();

    public static Intent replaceWithStub(Intent intent, String hostPackage) {
        if (intent == null) return null;
        
        android.content.ComponentName component = intent.getComponent();
        if (component == null) return intent;
        
        String targetPkg = component.getPackageName();
        String targetClass = component.getClassName();
        
        if (targetPkg != null && !targetPkg.equals(hostPackage)) {
            // Safety: Never wrap a StubActivity in another StubActivity
            if (targetClass != null && targetClass.contains("StubActivity")) {
                return intent;
            }
            
            Log.i(TAG, "OneCore-DEBUG: StubManager -> Redirecting " + targetClass);
            
            // Select stub based on Intent flags and launch requirements
            int flags = intent.getFlags();
            int procIndex = OneCoreProcessManager.getProcessIndex(targetPkg);
            String stubClass = "com.onecore.loader.StubActivity_P" + procIndex;

            // Lookup ActivityInfo for additional metadata like launchMode or taskAffinity
            android.content.pm.ActivityInfo ai = OneCorePackageManagerProxy.getActivityInfo(component);
            
            if (ai != null) {
                // If the app specifies a custom taskAffinity or a specific launchMode
                if (ai.launchMode == android.content.pm.ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
                    stubClass = "com.onecore.loader.StubActivity_P3";
                } else if (ai.launchMode == android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK) {
                    stubClass = "com.onecore.loader.StubActivity_P2";
                } else if (ai.taskAffinity != null && !ai.taskAffinity.equals(ai.packageName)) {
                    // Custom taskAffinity requested - use a dedicated task stub
                    stubClass = "com.onecore.loader.StubActivity_P2";
                }
            }

            // Also check flags
            if ((flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
                // If not already singleInstance, upgrade to P2 (singleTask)
                if (!stubClass.endsWith("P3")) {
                    stubClass = "com.onecore.loader.StubActivity_P2";
                }
            }
            
            Intent stubIntent = new Intent();
            stubIntent.setClassName(hostPackage, stubClass);
            stubIntent.putExtra("target_activity", targetClass);
            stubIntent.putExtra("target_package", targetPkg);
            // Include application class if known
            android.content.pm.ApplicationInfo vAi = VirtualContainer.getInstance().getAppInfo();
            if (vAi != null && vAi.className != null) {
                stubIntent.putExtra("target_application", vAi.className);
            }
            
            // Pass APK path for child process initialization
            String apkPath = VirtualContainer.getInstance().getApkPath();
            if (apkPath != null) {
                stubIntent.putExtra("target_apk_path", apkPath);
            }
            
            // Map original flags to stub
            stubIntent.setFlags(flags);
            
            if (intent.getExtras() != null) {
                stubIntent.putExtras(intent.getExtras());
            }
            stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            return stubIntent;
        }
        
        return intent;
    }
}
