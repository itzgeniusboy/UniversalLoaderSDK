package com.onecore.sdk.core;

import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxies the Activity Manager to lie about Runtime Identity (UID/PID).
 */
public class ActivityManagerHook implements InvocationHandler {
    private final Object realService;

    private ActivityManagerHook(Object realService) {
        this.realService = realService;
    }

    public static Object createProxy(Object realService) {
        java.util.Set<Class<?>> interfaces = new java.util.HashSet<>();
        Class<?> current = realService.getClass();
        while (current != null) {
            for (Class<?> iface : current.getInterfaces()) {
                interfaces.add(iface);
            }
            current = current.getSuperclass();
        }
        
        return Proxy.newProxyInstance(
                realService.getClass().getClassLoader(),
                interfaces.toArray(new Class[0]),
                new ActivityManagerHook(realService)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if (methodName.equals("startActivity") || methodName.equals("bindService") || methodName.equals("startService") || methodName.equals("stopService") || methodName.equals("registerReceiver") || methodName.equals("sendBroadcast")) {
            int intentIdx = -1;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof android.content.Intent) {
                        intentIdx = i;
                        break;
                    }
                }
            }

            if (methodName.equals("registerReceiver")) {
                // Track receivers for isolation if needed
            } else if (methodName.equals("sendBroadcast")) {
                // If it's a broadcast from guest app, we might want to redirect or modify it
            }

            if (intentIdx != -1) {
                android.content.Intent intent = (android.content.Intent) args[intentIdx];
                if (intent != null && intent.getComponent() != null) {
                    String pkgName = intent.getComponent().getPackageName();
                    String className = intent.getComponent().getClassName();

                    // If it's a guest activity/service launch, wrap it
                    if (CloneManager.getInstance().getClonedPackage(pkgName) != null && !intent.hasExtra("_VA_NON_HOOK_")) {
                        Logger.i("ActivityManagerHook", "AMS Interception: " + methodName + " for " + className);
                        
                        if (methodName.equals("startActivity")) {
                            android.content.Intent stubIntent = new android.content.Intent();
                            stubIntent.setClassName(com.onecore.sdk.OneCoreSDK.getContext().getPackageName(), "com.onecore.sdk.core.StubActivity");
                            
                            stubIntent.addFlags(intent.getFlags());
                            stubIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            
                            stubIntent.putExtra("target_package", pkgName);
                            stubIntent.putExtra("target_activity", className);
                            stubIntent.putExtra("EXTRA_TARGET_INTENT", new android.content.Intent(intent));
                            stubIntent.putExtra("_VA_NON_HOOK_", true);
                            
                            args[intentIdx] = stubIntent;
                            Logger.d("ActivityManagerHook", "Redirected to StubActivity");
                        } else if (methodName.equals("startService")) {
                            // Run the service in the virtual environment
                            ServiceManager.startService(com.onecore.sdk.OneCoreSDK.getContext(), intent);
                            return null;
                        } else if (methodName.equals("bindService")) {
                            android.os.IBinder binder = ServiceManager.bindService(com.onecore.sdk.OneCoreSDK.getContext(), intent);
                            // Real bind service involves ServiceConnection callback. This is a simplified direct binder return.
                            return binder != null;
                        } else if (methodName.equals("stopService")) {
                             // Handle service stop
                        }
                    }
                }
            }
        }

        if (methodName.equals("checkPermission")) {
             return PermissionManager.checkPermission((String)args[0], (int)args[1], (int)args[2]);
        }

        // Return Fake UID or PID if requested by the Game
        if (methodName.equals("getRunningAppProcesses")) {
            Object result = method.invoke(realService, args);
            if (result instanceof java.util.List) {
                java.util.List<android.app.ActivityManager.RunningAppProcessInfo> list = (java.util.List<android.app.ActivityManager.RunningAppProcessInfo>) result;
                for (android.app.ActivityManager.RunningAppProcessInfo info : list) {
                    if (info.pid == android.os.Process.myPid()) {
                        // Spoof our process name to be the guest app's package name
                        info.processName = BinderHookManager.sCurrentPackage;
                        info.pkgList = new String[]{BinderHookManager.sCurrentPackage};
                    }
                }
            }
            return result;
        }

        return method.invoke(realService, args);
    }
}
