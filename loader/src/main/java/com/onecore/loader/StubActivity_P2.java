package com.onecore.loader;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;

public class StubActivity_P2 extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.onecore.sdk.OneCoreSDK.init(getApplicationContext());
        super.onCreate(savedInstanceState);
        Log.i("OneCore-Stub", "StubActivity_P2 created, initializing environment...");
        
        String apkPath = getIntent().getStringExtra("target_apk_path");
        String pkgName = getIntent().getStringExtra("target_package");
        String appClass = getIntent().getStringExtra("target_application");
        
        if (apkPath != null && pkgName != null) {
            VirtualContainer.getInstance().installApk(this, apkPath, pkgName);
            if (appClass != null) {
                VirtualContainer.getInstance().bindApplication(this, appClass, pkgName);
            }
        }
    }
}
