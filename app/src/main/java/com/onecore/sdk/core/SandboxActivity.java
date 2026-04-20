package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
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
 * Sandbox Host: The Process that runs the Cloned App.
 * Solves: Context Mismatch and Namespace Isolation.
 */
public class SandboxActivity extends Activity {
    private static final String TAG = "SandboxActivity";
    private PackageInfo guestInfo;
    private DexClassLoader guestClassLoader;
    private Resources guestResources;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String originalPkg = getIntent().getStringExtra("target_package");
        String libPath = getIntent().getStringExtra("library_path");

        try {
            guestInfo = CloneManager.getInstance().getClonedPackage(originalPkg);
            if (guestInfo == null) throw new Exception("Metadata not found for " + originalPkg);

            Logger.i(TAG, "Booting Sandbox Process for: " + guestInfo.packageName);

            // 1. Load Resources (Skinning)
            AssetManager assets = AssetManager.class.newInstance();
            Method addAssetPath = assets.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assets, guestInfo.applicationInfo.sourceDir);
            guestResources = new Resources(assets, super.getResources().getDisplayMetrics(), super.getResources().getConfiguration());

            // 2. Load Guest Code (Isolated ClassLoader)
            File dexDir = new File(getFilesDir(), "sandbox_dex/" + originalPkg);
            if (!dexDir.exists()) dexDir.mkdirs();
            guestClassLoader = new DexClassLoader(
                    guestInfo.applicationInfo.sourceDir,
                    dexDir.getAbsolutePath(),
                    guestInfo.applicationInfo.nativeLibraryDir,
                    getClassLoader()
            );

            // 3. APPLY GLOBAL HOOKS (System Identity Fake)
            EnvironmentHooker.apply(this, guestInfo.packageName);

            // 4. FIX: LOAD ESP LIBRARY IN GUEST NAMESPACE
            if (libPath != null && new File(libPath).exists()) {
                Logger.i(TAG, "Injecting ESP Library into Sandbox Namespace: " + libPath);
                // We use System.load within the Host Activity which now shares 
                // the environment with the Guest DEX.
                System.load(libPath);
                Logger.d(TAG, "ESP Library linked successfully.");
            }

            // 5. Jump to Guest Entry Point
            launchGuest();

        } catch (Exception e) {
            Logger.e(TAG, "Sandbox Boot Failure", e);
            finish();
        }
    }

    private void launchGuest() throws Exception {
        String mainActivity = guestInfo.activities[0].name;
        Logger.d(TAG, "Starting Guest Entry: " + mainActivity);
        
        Class<?> clazz = guestClassLoader.loadClass(mainActivity);
        Intent intent = new Intent(this, clazz);
        intent.putExtras(getIntent());
        startActivity(intent);
    }

    @Override
    public ClassLoader getClassLoader() {
        return guestClassLoader != null ? guestClassLoader : super.getClassLoader();
    }

    @Override
    public Resources getResources() {
        return guestResources != null ? guestResources : super.getResources();
    }
}
