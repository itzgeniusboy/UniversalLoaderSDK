package com.onecore.sdk.core;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.onecore.sdk.NativeHookManager;
import com.onecore.sdk.VirtualContainer;
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
    private String libPath;
    private long lastHeartbeat = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Premium Stealth UI for Sandbox Host
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(0xFF000000);
        
        TextView tv = new TextView(this);
        tv.setText("VIRTUAL KERNEL BOOTING...");
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(14);
        layout.addView(tv);
        
        setContentView(layout);
        
        String originalPkg = getIntent().getStringExtra("target_package");
        this.libPath = getIntent().getStringExtra("library_path");

        try {
            // Early native initialization before any guest code runs
            String virtualRoot = getFilesDir().getAbsolutePath() + "/virtual/" + originalPkg;
            NativeHookManager.setupIsolation(this, virtualRoot, originalPkg);
            Logger.i(TAG, "Native Isolation Installed Successfully.");

            guestInfo = CloneManager.getInstance().getClonedPackage(originalPkg);
            
            // FALLBACK: If CloneManager cache is empty (due to separate process), use Intent Data
            if (guestInfo == null) {
                Logger.w(TAG, "Metadata cache empty in sandbox process. Using Intent Data fallback.");
                String sourceDir = getIntent().getStringExtra("source_dir");
                if (sourceDir != null) {
                    guestInfo = new PackageInfo();
                    guestInfo.packageName = originalPkg;
                    guestInfo.applicationInfo = new ApplicationInfo();
                    guestInfo.applicationInfo.packageName = originalPkg;
                    guestInfo.applicationInfo.sourceDir = sourceDir;
                    guestInfo.applicationInfo.dataDir = getIntent().getStringExtra("data_dir");
                    guestInfo.applicationInfo.nativeLibraryDir = getIntent().getStringExtra("native_lib_dir");
                }
            }

            if (guestInfo == null) {
                throw new Exception("Metadata not found for " + originalPkg);
            }

            Logger.i(TAG, "Booting Sandbox Process for: " + guestInfo.packageName);

            // 1. Load Resources
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
            VirtualContainer.getInstance().setGuestClassLoader(guestClassLoader);

            // 3. APPLY GLOBAL HOOKS
            EnvironmentHooker.apply(this, guestInfo.packageName);
            UidSpoofing.apply(this, 10000 + (int)(Math.random() * 5000)); // Sandbox UID

            // 4. LOAD ESP LIBRARY IN GUEST NAMESPACE
            if (libPath != null && new File(libPath).exists()) {
                System.load(libPath);
                Logger.d(TAG, "Guest Library linked successfully.");
            }

            // 5. Start Heartbeat to monitor Loader process
            startService(new Intent(this, SandboxHeartbeatService.class));
            startSandboxHeartbeat();

            // 6. Jump to Guest Entry Point
            launchGuestViaStub();
            sendLaunchResult(true, null);

        } catch (Exception e) {
            Logger.e(TAG, "Sandbox Boot Failure", e);
            sendLaunchResult(false, e.getMessage());
            finish();
        }
    }

    private void startSandboxHeartbeat() {
        new Thread(() -> {
            while (!isFinishing()) {
                try {
                    // If loader process disappears, sandbox should self-destruct
                    // For demo, we check if original parent PID is still 1 (after host dies) 
                    // or use a more complex Binder-based check in real scenarios.
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void sendLaunchResult(boolean success, String error) {
        Intent result = new Intent(VirtualContainer.ACTION_LAUNCH_RESULT);
        result.putExtra("success", success);
        if (error != null) result.putExtra("error", error);
        sendBroadcast(result);
    }

    private void launchGuestViaStub() throws Exception {
        // Find Launch Activity with MAIN/LAUNCHER intent filters
        String mainActivity = null;
        
        // HIGHLIGHT: Prioritize known BGMI/Unreal Engine entry points for 4.3.0
        if (guestInfo.packageName.contains("pubg") || guestInfo.packageName.contains("imobile")) {
            String[] variants = {
                "com.epicgames.ue4.SplashActivity",
                "com.tencent.tmgp.pubgmri.MainActivity",
                "com.tencent.tmgp.pubgm.MainActivity",
                "com.epicgames.ue4.GameActivity"
            };
            
            for (String variant : variants) {
                try {
                    guestClassLoader.loadClass(variant);
                    mainActivity = variant;
                    Logger.i(TAG, "Unreal Engine Entry Point Identified: " + mainActivity);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }
        }
        
        if (mainActivity == null && guestInfo.activities != null) {
            Logger.d(TAG, "Scanning manifest for launcher activities...");
            for (android.content.pm.ActivityInfo ai : guestInfo.activities) {
                if (ai.name.toLowerCase().contains("splash") || ai.name.toLowerCase().contains("launcher")) {
                    mainActivity = ai.name;
                    Logger.i(TAG, "Manifest match (Splash/Launcher): " + mainActivity);
                    break;
                }
            }
            if (mainActivity == null) {
                 for (android.content.pm.ActivityInfo ai : guestInfo.activities) {
                    if (ai.name.toLowerCase().contains("main")) {
                        mainActivity = ai.name;
                        Logger.i(TAG, "Manifest match (Main): " + mainActivity);
                        break;
                    }
                }
            }
            if (mainActivity == null && guestInfo.activities.length > 0) {
                mainActivity = guestInfo.activities[0].name;
                Logger.w(TAG, "No naming match. Using first activity: " + mainActivity);
            }
        }
        
        if (mainActivity == null) throw new Exception("No launchable activity found in guest APK.");
        
        Logger.d(TAG, "Relaying to StubHost for: " + mainActivity);
        Logger.i(TAG, "Final Launch Intent Configuration: PKG=" + guestInfo.packageName + " ACT=" + mainActivity);
        
        // CRITICAL FIX: We launch the StubActivity which IS in our manifest,
        // but we pass the target guest class name to it.
        Intent stubIntent = new Intent(this, StubActivity.class);
        stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        stubIntent.putExtra("target_activity", mainActivity);
        stubIntent.putExtra("target_package", guestInfo.packageName);
        stubIntent.putExtra("library_path", libPath);
        
        startActivity(stubIntent);
        
        Logger.i(TAG, "Redirected to Stub Process. Shell standby...");
        // Keep the shell alive for a moment to prevent process recycling
        new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing()) finish();
        }, 3000);
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
