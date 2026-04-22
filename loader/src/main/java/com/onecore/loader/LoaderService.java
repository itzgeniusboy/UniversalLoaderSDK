package com.onecore.loader;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import com.onecore.sdk.OneCoreSDK;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.Logger;

/**
 * Service to handle the virtualization environment initialization and management.
 */
public class LoaderService extends Service {
    private static final String TAG = "LoaderService";
    private VirtualDisplay virtualDisplay;
    private Context sandboxContext;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize virtual display for the sandbox
        VirtualDisplayManager vdm = VirtualDisplayManager.getInstance(this);
        virtualDisplay = vdm.createVirtualDisplay("SandboxDisplay", 1080, 1920, 480, null);
        
        // In a real scenario, sandboxContext would be initialized here or via OneCoreSDK
        this.sandboxContext = this; 
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void launchApp(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) return;
            
            // Target virtual display (critical for sandbox)
            if (virtualDisplay != null && Build.VERSION.SDK_INT >= 26) {
                intent.setLaunchDisplayId(virtualDisplay.getDisplayId());
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            
            // Use sandbox context instead of main context
            if (sandboxContext != null) {
                sandboxContext.startActivity(intent);
            } else {
                startActivity(intent); // fallback
            }
            
        } catch (Exception e) {
            Log.e("Loader", "Launch failed: " + e.getMessage());
        }
    }

    public static void initVirtualEnvironment(final String packageName, final VirtualContainer.LaunchCallback callback) {
        try {
            Logger.i(TAG, "Initializing virtual environment for: " + packageName);
            
            // Perform setup tasks in a safe environment
            OneCoreSDK.launchApp(packageName, new VirtualContainer.LaunchCallback() {
                @Override
                public void onLaunchSuccess() {
                    Logger.i(TAG, "Virtual environment setup SUCCESS for " + packageName);
                    if (callback != null) callback.onLaunchSuccess();
                }

                @Override
                public void onLaunchFailed(String reason) {
                    Logger.e(TAG, "Virtual environment setup FAILED: " + reason);
                    if (callback != null) callback.onLaunchFailed(reason);
                }
            });
            
        } catch (Exception e) {
            Logger.e(TAG, "CRITICAL ERROR during virtual environment initialization", e);
            if (callback != null) callback.onLaunchFailed("Service Error: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
    }
}
