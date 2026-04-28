package com.onecore.sdk.core.system.location;

import android.location.Location;

interface IBLocationManagerService {
    Location getLocation(String provider);
    void updateLocation(in Location location);
    boolean isLocationFaked();
}
