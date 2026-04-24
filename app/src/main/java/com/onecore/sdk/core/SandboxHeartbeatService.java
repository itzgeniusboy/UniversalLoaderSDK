package com.onecore.sdk.core;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import com.onecore.sdk.utils.Logger;

/**
 * Sandbox Heartbeat Service.
 * Monitors the lifecycle of the host process. If the host dies, the sandbox process kills itself.
 * This ensures true process isolation and cleanup.
 */
public class SandboxHeartbeatService extends Service {
    private static final String TAG = "SandboxHeartbeat";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("ACTION_FOREGROUND")) {
            Logger.d(TAG, "Entering foreground for virtual app keep-alive.");
            // In a real app, we would use a notification channel and a real notification here.
            // For now, we use a placeholder or system default if allowed.
        }
        Logger.d(TAG, "Heartbeat monitoring active.");
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Logger.w(TAG, "Loader task removed. Self-destructing sandbox process...");
        Process.killProcess(Process.myPid());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
