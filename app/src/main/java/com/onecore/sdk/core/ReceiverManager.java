package com.onecore.sdk.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import com.onecore.sdk.utils.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages virtual BroadcastReceivers.
 */
public class ReceiverManager {
    private static final String TAG = "OneCore-ReceiverMgr";
    private static final List<ReceiverRecord> sReceivers = new ArrayList<>();

    public static void registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter) {
        registerReceiver(context, receiver, filter, null, null, 0);
    }

    public static void registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter, int flags) {
        registerReceiver(context, receiver, filter, null, null, flags);
    }

    public static void registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, android.os.Handler scheduler, int flags) {
        if (receiver == null || filter == null) return;
        
        Logger.d(TAG, "Registering virtual receiver: " + receiver.getClass().getName() + " with flags: " + flags);
        synchronized (sReceivers) {
            sReceivers.add(new ReceiverRecord(receiver, filter));
        }
        
        // Register with the host context to receive system broadcasts
        try {
            // Android 13 (API 33) introduced registerReceiver(..., flags)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags);
            } else {
                context.registerReceiver(receiver, filter, broadcastPermission, scheduler);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to register receiver with host context", e);
        }
    }

    public static void unregisterReceiver(Context context, BroadcastReceiver receiver) {
        if (receiver == null) return;
        Logger.d(TAG, "Unregistering receiver: " + receiver.getClass().getName());
        synchronized (sReceivers) {
            sReceivers.removeIf(record -> record.receiver == receiver);
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (Exception ignored) {}
    }

    public static void sendBroadcast(Context context, android.content.Intent intent) {
        if (intent == null) return;
        
        Logger.d(TAG, "Sending virtual broadcast: " + intent.getAction());
        
        // Manual dispatch to local virtual receivers if we want isolation
        synchronized (sReceivers) {
            for (ReceiverRecord record : sReceivers) {
                if (record.filter != null && record.filter.matchAction(intent.getAction())) {
                    try {
                        record.receiver.onReceive(context, intent);
                    } catch (Exception e) {
                        Logger.e(TAG, "Local broadcast dispatch failed for " + record.receiver.getClass().getName(), e);
                    }
                }
            }
        }
        
        // Also send to the real system
        context.sendBroadcast(intent);
    }

    private static class ReceiverRecord {
        BroadcastReceiver receiver;
        IntentFilter filter;

        ReceiverRecord(BroadcastReceiver receiver, IntentFilter filter) {
            this.receiver = receiver;
            this.filter = filter;
        }
    }
}
