package com.onecore.sdk.core;

import android.app.Service;
import android.content.Intent;
import com.onecore.sdk.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Virtual Service lifecycle.
 * Prevents OS from seeing services that aren't declared in host manifest.
 */
public class ServiceManager {
    private static final String TAG = "OneCore-ServiceMgr";
    private static final Map<String, Service> mServices = new HashMap<>();
    private static final Map<String, android.os.IBinder> mBinders = new HashMap<>();

    public static void startService(android.content.Context context, Intent intent) {
        if (intent == null || intent.getComponent() == null) return;
        
        String className = intent.getComponent().getClassName();
        String pkgName = intent.getComponent().getPackageName();
        Logger.d(TAG, "Starting virtual service: " + className);
        
        if (mServices.containsKey(className)) {
            mServices.get(className).onStartCommand(intent, 0, 0);
            return;
        }

        try {
            ClassLoader cl = CloneManager.getInstance().getClassLoader();
            android.content.res.Resources res = CloneManager.getInstance().getResources();
            Service service = (Service) cl.loadClass(className).newInstance();
            
            // Apply ContextFixer before onCreate
            Context virtualContext = ContextManager.createVirtualContext(context, pkgName, cl, res);
            
            attachService(service, virtualContext, className, pkgName, cl, res);

            service.onCreate();
            service.onStartCommand(intent, 0, 0);
            mServices.put(className, service);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start virtual service " + className, e);
        }
    }

    public static android.os.IBinder bindService(android.content.Context context, Intent intent, Object connection) {
        if (intent == null || intent.getComponent() == null) return null;
        
        String className = intent.getComponent().getClassName();
        String pkgName = intent.getComponent().getPackageName();
        
        Logger.d(TAG, "Binding virtual service: " + className);
        
        if (!mServices.containsKey(className)) {
            startService(context, intent);
        }
        
        Service service = mServices.get(className);
        if (service != null) {
            android.os.IBinder binder = mBinders.get(className);
            if (binder == null) {
                try {
                    binder = service.onBind(intent);
                    mBinders.put(className, binder);
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to call onBind for " + className, e);
                }
            }
            
            // Notify ServiceConnection if possible
            if (connection != null && binder != null) {
                dispatchServiceConnected(connection, intent.getComponent(), binder);
            }
            
            return binder;
        }
        return null;
    }

    private static void dispatchServiceConnected(Object connection, android.content.ComponentName name, android.os.IBinder binder) {
        try {
            // IServiceConnection.connected(ComponentName, IBinder, boolean)
            // Signature: void connected(in ComponentName name, in IBinder service, boolean dead)
            Method connected = connection.getClass().getDeclaredMethod("connected", android.content.ComponentName.class, android.os.IBinder.class, boolean.class);
            connected.setAccessible(true);
            connected.invoke(connection, name, binder, false);
            Logger.v(TAG, "ServiceConnection.connected() dispatched successfully.");
        } catch (Exception e) {
             try {
                // Fallback for older versions (without boolean dead param)
                Method connected = connection.getClass().getDeclaredMethod("connected", android.content.ComponentName.class, android.os.IBinder.class);
                connected.setAccessible(true);
                connected.invoke(connection, name, binder);
            } catch (Exception e2) {
                Logger.e(TAG, "Failed to dispatch ServiceConnection: " + e.getMessage());
            }
        }
    }

    public static void unbindService(Object connection) {
        if (connection == null) return;
        Logger.d(TAG, "Unbind virtual service requested for connection: " + connection.getClass().getSimpleName());
    }

    public static void startForeground(Service service, int id, android.app.Notification notification) {
        Logger.i(TAG, "Service.startForeground() intercepted for id: " + id);
        // In a real implementation, we would either proxy this to a host notification channel
        // or show our own. For now, we simply log it to prevent crashes.
    }

    private static void attachService(Service service, Context virtualContext, String className, String pkgName, ClassLoader cl, android.content.res.Resources res) {
        try {
            java.lang.reflect.Method attach = Service.class.getDeclaredMethod("attach",
                android.content.Context.class,
                Class.forName("android.app.ActivityThread"),
                String.class,
                android.os.IBinder.class,
                android.app.Application.class,
                Object.class);
            attach.setAccessible(true);
            
            Object at = null;
            try {
                Class<?> atClass = Class.forName("android.app.ActivityThread");
                at = atClass.getDeclaredMethod("currentActivityThread").invoke(null);
            } catch (Exception ignored) {}

            attach.invoke(service, virtualContext, at, className, null, ApplicationManager.getVirtualApp(), null);
        } catch (Exception e) {
            Logger.w(TAG, "Standard attach failed, using context binding fallback: " + e.getMessage());
            ContextManager.bindContext(service, pkgName, cl, res);
        }
    }

    public static boolean stopService(Intent intent) {
        if (intent == null || intent.getComponent() == null) return false;
        String className = intent.getComponent().getClassName();
        Service service = mServices.remove(className);
        if (service != null) {
            String pkgName = intent.getComponent().getPackageName();
            android.os.IBinder binder = mBinders.remove(className);
            if (binder != null) {
                try {
                    service.onUnbind(intent);
                } catch (Exception ignored) {}
            }
            service.onDestroy();
            Logger.d(TAG, "Virtual service STOPPED: " + className);
            return true;
        }
        return false;
    }
}
