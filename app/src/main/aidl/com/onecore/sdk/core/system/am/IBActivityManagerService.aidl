package com.onecore.sdk.core.system.am;

import android.content.Intent;
import android.content.ComponentName;
import android.os.IBinder;

interface IBActivityManagerService {
    ComponentName startActivity(in Intent intent, int userId);
    int startService(in Intent intent, int userId);
    int stopService(in Intent intent, int userId);
    boolean bindService(in Intent intent, in IBinder connection, int userId);
    void unbindService(in IBinder connection, int userId);
    void broadcastIntent(in Intent intent, int userId);
}
