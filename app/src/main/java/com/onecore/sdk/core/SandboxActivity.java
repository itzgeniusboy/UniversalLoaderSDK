package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import com.onecore.sdk.utils.Logger;
import java.io.File;
import java.lang.reflect.Method;
import dalvik.system.DexClassLoader;

/**
 * The Host Activity that actually loads the Cloned App's code and resources.
 * This is the "Heart" of the Real Non-Root Cloning mechanism.
 */
public class SandboxActivity extends Activity {
    private static final String TAG = "SandboxActivity";
    private String targetPackage;
    private Resources targetResources;
    private DexClassLoader targetClassLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        targetPackage = getIntent().getStringExtra("target_package");
        if (targetPackage == null) {
            finish();
            return;
        }

        Logger.i(TAG, "Starting Sandbox Host for: " + targetPackage);

        try {
            // 1. Prepare Target Info
            PackageInfo packageInfo = CloneManager.getInstance().getClonedPackage(targetPackage);
            ApplicationInfo appInfo = packageInfo.applicationInfo;

            // 2. Load Resources (Skinning the host as the guest)
            loadTargetResources(appInfo.sourceDir);

            // 3. Setup ClassLoader (DEX Loading)
            loadTargetCode(appInfo.sourceDir);

            // 4. Hook Environment for this Process
            // This is where we lie to the game about its identity
            EnvironmentHooker.apply(this, targetPackage);

            // 5. Find Target Main Activity and Launch it
            launchTargetMainActivity(packageInfo);

        } catch (Exception e) {
            Logger.e(TAG, "Failed to initialize sandbox environment", e);
            finish();
        }
    }

    private void loadTargetResources(String apkPath) throws Exception {
        AssetManager assetManager = AssetManager.class.newInstance();
        Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
        addAssetPath.invoke(assetManager, apkPath);
        
        Resources superRes = super.getResources();
        targetResources = new Resources(assetManager, superRes.getDisplayMetrics(), superRes.getConfiguration());
    }

    private void loadTargetCode(String apkPath) {
        File dexCache = new File(getFilesDir(), "dex_cache/" + targetPackage);
        if (!dexCache.exists()) dexCache.mkdirs();

        targetClassLoader = new DexClassLoader(
                apkPath,
                dexCache.getAbsolutePath(),
                null,
                getClassLoader()
        );
        
        // Replace current ActivityThread classloader (Deep Hook)
        Logger.d(TAG, "Code loaded via custom DexClassLoader.");
    }

    private void launchTargetMainActivity(PackageInfo info) throws Exception {
        String mainClassName = info.activities[0].name; // Simplified for this example
        Logger.d(TAG, "Attempting to launch target activity: " + mainClassName);
        
        Class<?> targetClass = targetClassLoader.loadClass(mainClassName);
        Intent intent = new Intent(this, targetClass);
        // Copy extras to ensure game flow
        intent.putExtras(getIntent());
        
        // This would typically involve starting the activity manually via reflection 
        // to bypass system registration if it's not declared in our manifest
        startActivity(intent);
    }

    @Override
    public Resources getResources() {
        return targetResources != null ? targetResources : super.getResources();
    }

    @Override
    public ClassLoader getClassLoader() {
        return targetClassLoader != null ? targetClassLoader : super.getClassLoader();
    }
}
