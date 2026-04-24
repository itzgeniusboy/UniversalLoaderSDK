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

    private Object createContentProviderHolder(android.content.pm.ProviderInfo info, Object providerProxy) {
        try {
            Class<?> holderClass;
            try {
                holderClass = Class.forName("android.app.ContentProviderHolder");
            } catch (ClassNotFoundException e) {
                holderClass = Class.forName("android.app.IActivityManager$ContentProviderHolder");
            }
            
            Object holder;
            try {
                holder = holderClass.getConstructor(android.content.pm.ProviderInfo.class).newInstance(info);
            } catch (Exception e) {
                holder = holderClass.newInstance();
                java.lang.reflect.Field infoField = holderClass.getDeclaredField("info");
                infoField.setAccessible(true);
                infoField.set(holder, info);
            }
            
            java.lang.reflect.Field providerField = holderClass.getDeclaredField("provider");
            providerField.setAccessible(true);
            providerField.set(holder, providerProxy);
            
            try {
                java.lang.reflect.Field noReleaseField = holderClass.getDeclaredField("noReleaseNeeded");
                noReleaseField.setAccessible(true);
                noReleaseField.set(holder, true);
            } catch (Exception ignored) {}
            
            return holder;
        } catch (Exception e) {
            Logger.e("ActivityManagerHook", "Failed to create ContentProviderHolder", e);
            return null;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if (methodName.equals("startActivity") || methodName.equals("bindService") || methodName.equals("startService") || methodName.equals("stopService") || methodName.equals("registerReceiver") || methodName.equals("unregisterReceiver") || methodName.equals("sendBroadcast")) {
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
                // We've already wrapped it in ReceiverManager if called via Context
            } else if (methodName.equals("sendBroadcast")) {
                android.content.Intent intent = (android.content.Intent) args[intentIdx];
                if (intent != null) {
                    ReceiverManager.sendBroadcast(com.onecore.sdk.OneCoreSDK.getContext(), intent);
                    return null; // Skip real system broadcast as we handled it
                }
            } else if (methodName.equals("getContentProvider") || methodName.equals("getContentProviderExternal")) {
                String authority = null;
                // Heuristic to find the authority name: it's a String that isn't a package name
                // and usually appears after callingPkg/attributionTag
                for (Object arg : args) {
                    if (arg instanceof String) {
                        String s = (String) arg;
                        if (s.contains(".") || !s.isEmpty()) {
                            // Check if it's a resolved authority in our registry
                            if (com.onecore.sdk.core.pm.VirtualPackageManager.resolveProviderByAuthority(s) != null) {
                                authority = s;
                                break;
                            }
                        }
                    }
                }
                
                if (authority != null) {
                    android.content.pm.ProviderInfo pi = com.onecore.sdk.core.pm.VirtualPackageManager.resolveProviderByAuthority(authority);
                if (pi != null) {
                    Logger.d("ActivityManagerHook", "IActivityManager." + methodName + " spoof: " + authority);
                    
                    android.content.ContentProvider localProvider = ProviderInstaller.getInstalledProvider(pi.packageName, pi.name);
                    if (localProvider == null) {
                        // Attempt lazy install if not already installed
                        android.app.Application app = ApplicationManager.getVirtualApp(pi.packageName);
                        if (app != null) {
                            localProvider = ProviderInstaller.install(app, pi);
                        }
                    }

                    if (localProvider != null) {
                        Object proxy = ContentResolverHook.createProxy(localProvider);
                        if (proxy != null) {
                            return createContentProviderHolder(pi, proxy);
                        }
                    }
                }
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
                            Object connection = args[4]; // IServiceConnection
                            android.os.IBinder binder = ServiceManager.bindService(com.onecore.sdk.OneCoreSDK.getContext(), intent, connection);
                            return binder != null ? 1 : 0; // Return 1 for success in newer AMS
                        } else if (methodName.equals("unbindService")) {
                             ServiceManager.unbindService(args[0]);
                             return true;
                        } else if (methodName.equals("stopService")) {
                             // Handle service stop
                        } else if (methodName.equals("setServiceForeground")) {
                             android.content.ComponentName cn = (android.content.ComponentName) args[0];
                             int id = (int) args[2];
                             android.app.Notification notification = (android.app.Notification) args[3];
                             boolean removeNotification = (args.length > 4) && (boolean) args[4];
                             
                             if (notification != null) {
                                  // In a full implementation we'd find the Service object from className
                                  // For now, passing notification implies startForeground
                                  Logger.d("ActivityManagerHook", "setServiceForeground: START id=" + id);
                             } else {
                                  Logger.d("ActivityManagerHook", "setServiceForeground: STOP");
                             }
                             return null;
                        }
                    }
                }
            }
        }

        if (methodName.equals("checkPermission")) {
             return PermissionManager.checkPermission((String)args[0], (String)args[1], (int)args[2]);
        }

        if (methodName.equals("checkUriPermission")) {
             return android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        // Return Fake UID or PID if requested by the Game
        if (methodName.equals("getRunningAppProcesses") || methodName.equals("getHistoricalProcessExitReasons")) {
            Object result = method.invoke(realService, args);
            if (result instanceof java.util.List) {
                java.util.List list = (java.util.List) result;
                String pkgName = BinderHookManager.sCurrentPackage;
                
                java.util.Set<String> virtualProcesses = com.onecore.sdk.core.pm.VirtualPackageManager.getAllProcessNames(pkgName);
                
                for (Object item : list) {
                    if (item instanceof android.app.ActivityManager.RunningAppProcessInfo) {
                        android.app.ActivityManager.RunningAppProcessInfo info = (android.app.ActivityManager.RunningAppProcessInfo) item;
                        if (info.pid == android.os.Process.myPid()) {
                            info.processName = pkgName;
                            info.pkgList = new String[]{pkgName};
                        }
                    }
                }
                
                // Add virtual processes that aren't the main one to the list to fool multi-process apps
                if (virtualProcesses != null) {
                    for (String vProc : virtualProcesses) {
                        if (!vProc.equals(pkgName)) {
                            android.app.ActivityManager.RunningAppProcessInfo fake = new android.app.ActivityManager.RunningAppProcessInfo();
                            fake.processName = vProc;
                            fake.pid = android.os.Process.myPid() + 100; // Fake PID
                            fake.uid = android.os.Process.myUid();
                            fake.pkgList = new String[]{pkgName};
                            list.add(fake);
                        }
                    }
                }
                
                return list;
            }
            return result;
        }

        if (methodName.equals("getRunningServices")) {
             Object result = method.invoke(realService, args);
             // We could filter this to only show virtual services
             return result;
        }

        if (methodName.equals("getProcessMemoryInfo") || methodName.equals("getMemoryInfo")) {
             // Performance optimization: we might want to return slightly tweaked values to prevent anti-cheat detection of virtualization
        }

        return method.invoke(realService, args);
    }
}
