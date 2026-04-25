package com.onecore.sdk.core;

import android.content.Intent;
import android.util.Log;
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
            Log.i(TAG, "OneCore-DEBUG: StubManager -> Redirecting " + targetClass);
            
            Intent stubIntent = new Intent();
            stubIntent.setClassName(hostPackage, "com.onecore.loader.StubActivity");
            stubIntent.putExtra("target_activity", targetClass);
            stubIntent.putExtra("target_package", targetPkg);
            
            if (intent.getExtras() != null) {
                stubIntent.putExtras(intent.getExtras());
            }
            stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            return stubIntent;
        }
        
        return intent;
    }
}
