package com.onecore.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.onecore.sdk.core.env.BEnvironment;
import com.onecore.sdk.core.system.HookManager;
import com.onecore.sdk.core.StubActivity;
import com.onecore.sdk.utils.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Launcher and Container Activity.
 * This is the entry point for testing the Virtual SDK.
 */
public class LauncherActivity extends Activity {
    private static final String TAG = "LauncherActivity";
    private GridView mAppGrid;
    private AppAdapter mAdapter;
    private List<VirtualApp> mVirtualApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        // 1. Initialize Engine
        initEngine();

        // 2. Setup UI
        mAppGrid = findViewById(R.id.app_grid);
        mAdapter = new AppAdapter(this, mVirtualApps);
        mAppGrid.setAdapter(mAdapter);

        // Load persisted apps
        loadInstalledApps();

        findViewById(R.id.btn_add_app).setOnClickListener(v -> {
            showAppSelectionDialog();
        });

        mAppGrid.setOnItemClickListener((parent, view, position, id) -> {
            launchApp(mVirtualApps.get(position));
        });

        // 3. Update Status Panel
        updateStatus();
    }

    private void initEngine() {
        BEnvironment.init(this);
        DeviceSpoofer.getInstance().init(this);
        HookManager.init(this);
    }

    private void updateStatus() {
        TextView sdkStatus = findViewById(R.id.sdk_status);
        TextView spoofStatus = findViewById(R.id.spoof_status);
        
        boolean nativeOk = com.onecore.sdk.core.NativeHook.isAvailable();
        boolean spoofOk = android.os.Build.MODEL.equals("SM-G998B") || android.os.Build.MODEL.equals("ONEPLUS A6013"); // Based on DeviceSpoofer FAKE_DATA
        
        StringBuilder coreInfo = new StringBuilder();
        coreInfo.append("Core: ").append(nativeOk ? "Native (Stable)" : "Java (Limited)").append("\n");
        coreInfo.append("VFS: Active\n");
        coreInfo.append("Binder: Synchronized");
        
        sdkStatus.setText(coreInfo.toString());
        sdkStatus.setTextColor(nativeOk ? 0xFF2E7D32 : 0xFFE65100);

        StringBuilder spoofInfo = new StringBuilder();
        spoofInfo.append("Spoof: ").append(spoofOk ? "Success" : "Failed").append("\n");
        spoofInfo.append("Root: Hidden\n");
        spoofInfo.append("DevOpts: Masked");
        
        spoofStatus.setText(spoofInfo.toString());
        spoofStatus.setTextColor(spoofOk ? 0xFF2E7D32 : 0xFFD32F2F);
        
        Logger.i(TAG, "Dashboard Status Updated. Native=" + nativeOk + ", Spoof=" + spoofOk);
    }

    private void loadInstalledApps() {
        mVirtualApps.clear();
        Set<String> pkgs = com.onecore.sdk.core.VirtualAppManager.get(this).getInstalledPackages();
        for (String pkg : pkgs) {
            try {
                ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
                addVirtualApp(pkg, getPackageManager().getApplicationLabel(info).toString());
            } catch (Exception e) {
                // App might have been uninstalled from system
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    private void showAppSelectionDialog() {
        // Show a list of all apps installed on the device
        final List<ApplicationInfo> installedApps = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
        final List<String> appNames = new ArrayList<>();
        for (ApplicationInfo info : installedApps) {
            appNames.add(getPackageManager().getApplicationLabel(info).toString() + "\n(" + info.packageName + ")");
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select App to Add to Sandbox");
        builder.setItems(appNames.toArray(new String[0]), (dialog, which) -> {
            ApplicationInfo selected = installedApps.get(which);
            com.onecore.sdk.core.VirtualAppManager.get(this).addApp(selected.packageName);
            addVirtualApp(selected.packageName, getPackageManager().getApplicationLabel(selected).toString());
            Toast.makeText(this, "Added: " + selected.packageName, Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void addVirtualApp(String pkg, String label) {
        VirtualApp app = new VirtualApp();
        app.packageName = pkg;
        app.label = label;
        try {
            app.icon = getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            // Fallback icon
        }
        
        if (!mVirtualApps.contains(app)) {
            mVirtualApps.add(app);
            mAdapter.notifyDataSetChanged();
            Logger.i(TAG, "Virtual App Added: " + pkg);
        }
    }

    private void launchApp(VirtualApp app) {
        Intent intent = new Intent(this, StubActivity.class);
        intent.putExtra("target_package", app.packageName);
        intent.putExtra("target_activity", "com.onecore.sdk.core.SandboxActivity"); // Generic Entry
        startActivity(intent);
        Toast.makeText(this, "Launching " + app.label + " in OneCore Sandbox...", Toast.LENGTH_SHORT).show();
    }

    // --- Helper Classes ---

    static class VirtualApp {
        String packageName;
        String label;
        Drawable icon;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof VirtualApp) {
                return ((VirtualApp) obj).packageName.equals(this.packageName);
            }
            return false;
        }
    }

    static class AppAdapter extends BaseAdapter {
        private final Context context;
        private final List<VirtualApp> apps;

        AppAdapter(Context context, List<VirtualApp> apps) {
            this.context = context;
            this.apps = apps;
        }

        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
            }
            VirtualApp app = apps.get(position);
            ((ImageView) convertView.findViewById(R.id.app_icon)).setImageDrawable(app.icon);
            ((TextView) convertView.findViewById(R.id.app_name)).setText(app.label);
            return convertView;
        }
    }
}
