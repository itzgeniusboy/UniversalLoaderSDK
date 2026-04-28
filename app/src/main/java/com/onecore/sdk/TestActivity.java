package com.onecore.sdk;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import com.onecore.sdk.core.env.BEnvironment;
import com.onecore.sdk.core.system.HookManager;
import com.onecore.sdk.utils.Logger;

/**
 * Easy Test Activity to verify SDK functionality.
 */
public class TestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView tv = new TextView(this);
        tv.setPadding(50, 50, 50, 50);
        tv.setTextSize(18);
        setContentView(tv);

        StringBuilder sb = new StringBuilder();
        sb.append("--- OneCore SDK Test ---\n\n");

        try {
            // 1. Init Environment
            BEnvironment.init(this);
            sb.append("✅ Environment: Initialized\n");
            sb.append("Virtual Root: ").append(BEnvironment.getVirtualRoot().getPath()).append("\n\n");

            // 2. Initializing Device Spoofing
            DeviceSpoofer.getInstance().init(this);
            sb.append("✅ Device Spoofing: Active\n");
            sb.append("Spoofed Model: ").append(Build.MODEL).append("\n");
            sb.append("Spoofed Manufacturer: ").append(Build.MANUFACTURER).append("\n\n");

            // 3. Installing Hooks
            HookManager.init(this);
            sb.append("✅ System Hooks: Installed\n");
            
            // 4. Native Check
            if (com.onecore.sdk.core.NativeHook.isAvailable()) {
                sb.append("✅ Native Layer: Connected\n");
            } else {
                sb.append("❌ Native Layer: Not Found (Shared Library Missing)\n");
            }

            sb.append("\nStatus: SDK Logic is RUNNING.\n");
            sb.append("Check Logcat (tag: OneCore) for detailed hook traces.");

        } catch (Exception e) {
            sb.append("❌ Test Failed: ").append(e.getMessage());
            Logger.e("TestActivity", "Test Error", e);
        }

        tv.setText(sb.toString());
    }
}
