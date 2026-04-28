package com.onecore.sdk.core.system.am;

import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import com.onecore.sdk.core.ServiceManager;
import com.onecore.sdk.utils.Logger;

/**
 * Server-side implementation of Virtual Activity Manager.
 * Handles cross-process requests from virtual apps.
 */
public class BActivityManagerService extends IBActivityManagerService.Stub {
    private static final String TAG = "BActivityManagerService";
    private static final BActivityManagerService sService = new BActivityManagerService();

    public static BActivityManagerService get() {
        return sService;
    }

    @Override
    public ComponentName startActivity(Intent intent, int userId) throws RemoteException {
        // Implementation for task management
        Logger.i(TAG, "Virtual StartActivity: " + intent);
        return intent.getComponent();
    }

    @Override
    public int startService(Intent intent, int userId) throws RemoteException {
        com.onecore.sdk.core.system.VirtualServiceManager.getInstance().startService(intent);
        return 0;
    }

    @Override
    public int stopService(Intent intent, int userId) throws RemoteException {
        return 1;
    }

    @Override
    public boolean bindService(Intent intent, IBinder connection, int userId) throws RemoteException {
        return com.onecore.sdk.core.system.VirtualServiceManager.getInstance().onBindService(intent) != null;
    }

    @Override
    public void unbindService(IBinder connection, int userId) throws RemoteException {
        ServiceManager.unbindService(connection);
    }

    @Override
    public void broadcastIntent(Intent intent, int userId) throws RemoteException {
        // Broadcast management
    }
}
