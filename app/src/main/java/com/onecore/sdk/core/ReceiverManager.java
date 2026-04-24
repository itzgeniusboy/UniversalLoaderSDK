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
        if (receiver == null || filter == null) return;
        
        Logger.d(TAG, "Registering virtual receiver: " + receiver.getClass().getName());
        synchronized (sReceivers) {
            sReceivers.add(new ReceiverRecord(receiver, filter));
        }
        
        // We also need to register it with the real system context if it needs system broadcasts
        // but often the virtual app just wants its own communication.
        context.registerReceiver(receiver, filter);
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
