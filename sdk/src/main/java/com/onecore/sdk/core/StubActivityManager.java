package com.onecore.sdk.core;

import android.content.Intent;
import android.util.Log;

/**
 * Manages the mapping between real guest activities and host stub activities.
 */
public class StubActivityManager {
    private static final String TAG = "OneCore-StubManager";

    /**
     * Replaces the target activity in the intent with a StubActivity.
     * Stores the real target info in the intent extras.
     */
    public static Intent replaceWithStub(Intent intent, String hostPackage) {
        if (intent == null) return null;
        
        android.content.ComponentName component = intent.getComponent();
        if (component == null) return intent;
        
        String targetPkg = component.getPackageName();
        String targetClass = component.getClassName();
        
        // redirect if it's a guest app (not host)
        if (targetPkg != null && !targetPkg.equals(hostPackage)) {
            Log.i(TAG, "OneCore-DEBUG: Redirecting -> StubActivity. Target: " + targetClass);
            
            Intent stubIntent = new Intent();
            // Using StubActivity defined in manifest
            stubIntent.setClassName(hostPackage, "com.onecore.loader.StubActivity");
            
            // Save metadata for restoration in Instrumentation
            stubIntent.putExtra("target_activity", targetClass);
            stubIntent.putExtra("target_package", targetPkg);
            
            // Copy extras and flags
            if (intent.getExtras() != null) {
                stubIntent.putExtras(intent.getExtras());
            }
            stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            return stubIntent;
        }
        
        return intent;
    }
}
