package com.onecore.sdk.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages guest broadcast receivers.
 */
public class OneCoreBroadcastManager {
    private static final String TAG = "OneCore-Broadcast";
    private static final List<BroadcastReceiver> mRegisteredReceivers = new ArrayList<>();

    public static void registerReceivers(Context context, PackageInfo packageInfo) {
        if (packageInfo.receivers == null) return;

        Log.i(TAG, "OneCore-DEBUG: Registering guest receivers for " + packageInfo.packageName);
        
        for (ActivityInfo receiverInfo : packageInfo.receivers) {
            try {
                // In a real engine, we'd parse intent filters from the manifest
                // For this implementation, we simulate registering one for a common action
                // if the developer wants more, they'd need a full manifest parser.
                
                ClassLoader cl = VirtualContainer.getInstance().getClassLoader();
                Class<?> receiverClass = cl.loadClass(receiverInfo.name);
                final BroadcastReceiver receiver = (BroadcastReceiver) receiverClass.newInstance();
                
                // In a perfect engine, we'd use a ManifestParser to get IntentFilters
                // For this implementation, we allow manual registration or simulate common ones
                IntentFilter filter = new IntentFilter();
                // Simulation: if receiver is named 'BootReceiver', listen for BOOT_COMPLETED
                if (receiverInfo.name.toLowerCase().contains("boot")) {
                    filter.addAction(Intent.ACTION_BOOT_COMPLETED);
                }
                
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    // RECEIVER_EXPORTED = 2, RECEIVER_NOT_EXPORTED = 4
                    int flags = receiverInfo.exported ? 2 : 4;
                    context.registerReceiver(receiver, filter, flags);
                } else {
                    context.registerReceiver(receiver, filter);
                }
                
                Log.d(TAG, "Registered receiver: " + receiverInfo.name);
                mRegisteredReceivers.add(receiver);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to register receiver: " + receiverInfo.name, e);
            }
        }
    }
}
