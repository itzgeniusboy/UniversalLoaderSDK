package com.onecore.sdk;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles device information spoofing and real GPS mocking.
 */
public class DeviceSpoofer {
    private static final String TAG = "DeviceSpoofer";
    private static DeviceSpoofer instance;
    private final Map<String, Object> fakeData = new HashMap<>();

    private DeviceSpoofer() {}

    public static synchronized DeviceSpoofer getInstance() {
        if (instance == null) {
            instance = new DeviceSpoofer();
        }
        return instance;
    }

    public void init(Context context) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        setDefaultFakeData();
        applyGlobalSpoof();
        Logger.d(TAG, "Device Spoofer initialized.");
    }

    private void setDefaultFakeData() {
        fakeData.put("MODEL", "Pixel 7 Pro");
        fakeData.put("BRAND", "google");
        fakeData.put("MANUFACTURER", "Google");
        fakeData.put("PRODUCT", "cheetah");
        fakeData.put("SERIAL", "1234567890ABCDEF");
    }

    public void applyGlobalSpoof() {
        try {
            for (Map.Entry<String, Object> entry : fakeData.entrySet()) {
                setFinalStaticField(Build.class, entry.getKey(), entry.getValue());
            }
            Logger.d(TAG, "Build info hardware-masked.");
        } catch (Exception e) {
            Logger.e(TAG, "Spoofing failed", e);
        }
    }

    private void setFinalStaticField(Class<?> clazz, String fieldName, Object newValue) throws Exception {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, newValue);
        } catch (NoSuchFieldException ignored) {}
    }

    /**
     * Real GPS Mocking using LocationManager Test Provider.
     */
    public void spoofLocation(Context context, double lat, double lon) {
        if (!SDKLicense.getInstance().isLicensed()) return;
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            String provider = LocationManager.GPS_PROVIDER;

            lm.addTestProvider(provider, false, false, false, false, true, true, true, 0, 5);
            lm.setTestProviderEnabled(provider, true);

            Location mockLocation = new Location(provider);
            mockLocation.setLatitude(lat);
            mockLocation.setLongitude(lon);
            mockLocation.setAltitude(0);
            mockLocation.setTime(System.currentTimeMillis());
            mockLocation.setAccuracy(1.0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            }

            lm.setTestProviderLocation(provider, mockLocation);
            Logger.d(TAG, "GPS spoofed to: " + lat + ", " + lon);
        } catch (Exception e) {
            Logger.e(TAG, "GPS spoof failed (Check Mock Location permission)", e);
        }
    }
}
