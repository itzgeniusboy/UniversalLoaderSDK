package com.onecore.sdk.core;

import android.util.Log;
import com.onecore.sdk.core.reflex.ReflectionHelper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Spoofs device identifiers like IMEI, Serial, and Android ID.
 */
public class OneCoreDeviceSpoofing implements InvocationHandler {
    private static final String TAG = "OneCore-DeviceSpoof";
    private final Object mBase;

    public OneCoreDeviceSpoofing(Object base) {
        this.mBase = base;
    }

    public static void install() {
        SafeExecutionManager.run("Device ID Spoofing", () -> {
            // Hook TelephonyManager (iphonesubinfo)
            Object iphoneSubInfo = ReflectionHelper.invokeMethod(null, "getService", "iphonesubinfo");
            if (iphoneSubInfo != null) {
                // In a real environment, we'd wrap the IBinder proxy here.
                Log.d(TAG, "Telephony registry found for spoofing.");
            }
            Log.i(TAG, "OneCore-DEBUG: Device ID spoofing core initialized.");
        });
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("getDeviceId".equals(name) || "getImei".equals(name)) {
            return "86" + UUID.randomUUID().toString().substring(0, 13).replaceAll("[^0-9]", "0");
        }
        if ("getSimSerialNumber".equals(name)) {
            return "898600" + UUID.randomUUID().toString().substring(0, 14).replaceAll("[^0-9]", "0");
        }
        return method.invoke(mBase, args);
    }
}
