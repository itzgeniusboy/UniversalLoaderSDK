package com.onecore.sdk.core.system.location;

import android.location.Location;
import android.os.RemoteException;

/**
 * Implementation to spoof location for the virtual app.
 */
public class BLocationManagerService extends IBLocationManagerService.Stub {
    private static final BLocationManagerService sService = new BLocationManagerService();
    private Location mFakeLocation;

    public static BLocationManagerService get() {
        return sService;
    }

    @Override
    public Location getLocation(String provider) throws RemoteException {
        if (mFakeLocation == null) {
            mFakeLocation = new Location(provider);
            mFakeLocation.setLatitude(28.6139); // Default to New Delhi
            mFakeLocation.setLongitude(77.2090);
        }
        return mFakeLocation;
    }

    @Override
    public void updateLocation(Location location) throws RemoteException {
        this.mFakeLocation = location;
    }

    @Override
    public boolean isLocationFaked() throws RemoteException {
        return true;
    }
}
