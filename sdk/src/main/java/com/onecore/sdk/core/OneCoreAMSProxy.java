package com.onecore.sdk.core;

import android.util.Log;
import com.onecore.sdk.VirtualContainer;
import com.onecore.sdk.utils.ReflectionHelper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxy for IActivityManager / IActivityTaskManager to intercept startActivity.
 * Adaptive logic for Android 10-17.
 */
public class OneCoreAMSProxy implements InvocationHandler {
    private static final String TAG = "OneCore-AMSProxy";
    private final Object mBase;
    private static String sHostPackage;

    public OneCoreAMSProxy(Object base) {
        this.mBase = base;
    }

    public static void install(String hostPackage) {
        sHostPackage = hostPackage;
        SafeExecutionManager.run("AMS Hook", () -> {
            Object singleton = null;
            
            // Try ActivityTaskManager (Android 10+)
            if (SystemVersionManager.isAndroid10OrAbove()) {
                try {
                    Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
                    singleton = ReflectionHelper.getFieldValue(atmClass, "IActivityTaskManagerSingleton");
                } catch (Exception ignored) {}
            }
            
            // Fallback to ActivityManager
            if (singleton == null) {
                try {
                    Class<?> amClass = Class.forName("android.app.ActivityManager");
                    singleton = ReflectionHelper.getFieldValue(amClass, "IActivityManagerSingleton", "gDefault");
                } catch (Exception ignored) {}
            }
            
            if (singleton == null) {
                throw new RuntimeException("Could not find AMS/ATMS singleton");
            }
            
            Object rawInstance = ReflectionHelper.invokeMethod(singleton, "get");
            if (rawInstance == null) {
                rawInstance = ReflectionHelper.getFieldValue(singleton, "mInstance");
            }

            if (rawInstance == null) {
                 throw new RuntimeException("Could not get AMS instance from singleton");
            }

            // Check if already hooked
            if (rawInstance.getClass().getName().contains("com.onecore.sdk.core")) {
                Log.w(TAG, "AMS/ATMS already hooked, skipping.");
                return;
            }

            Class<?> iAmClass = null;
            for (Class<?> iface : rawInstance.getClass().getInterfaces()) {
                String name = iface.getName();
                if (name.contains("IActivityManager") || name.contains("IActivityTaskManager")) {
                    iAmClass = iface;
                    break;
                }
            }

            if (iAmClass == null) {
                 throw new RuntimeException("Could not find IActivityManager interface");
            }

            Object proxy = Proxy.newProxyInstance(
                iAmClass.getClassLoader(),
                new Class[]{iAmClass},
                new OneCoreAMSProxy(rawInstance)
            );
            
            ReflectionHelper.setFieldValue(singleton, proxy, "mInstance");
            Log.i(TAG, "OneCore-DEBUG: AMS/ATMS hooked successfully on " + SystemVersionManager.getAMServiceName());
        });
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        
        if (methodName.startsWith("startActivity")) {
             int intentIndex = -1;
             for (int i = 0; i < args.length; i++) {
                 if (args[i] instanceof android.content.Intent) {
                     intentIndex = i;
                     break;
                 }
             }
             if (intentIndex != -1) {
                 android.content.Intent intent = (android.content.Intent) args[intentIndex];
                 // Skip if it's already a stub or system activity
                 if (intent.getComponent() != null && !intent.getComponent().getClassName().contains("StubActivity")
                     && !intent.getComponent().getPackageName().equals("android")) {
                     android.content.Intent rewritten = OneCoreStubManager.replaceWithStub(intent, sHostPackage);
                     if (rewritten != intent) {
                         Log.d(TAG, "AMS Proxy: Redirecting " + intent.getComponent() + " for method " + methodName);
                         args[intentIndex] = rewritten;
                         
                         // Fix calling package if present (usually at index 1 or 2)
                         for (int i = 0; i < args.length; i++) {
                             if (args[i] instanceof String && args[i].equals(sHostPackage)) {
                                 // Some methods require the real host package for permission checks, 
                                 // but some require the virtual one. We keep host for system verification.
                             }
                         }
                     }
                 }
             }
        } else if (methodName.equals("getIntentSender") || methodName.equals("getIntentSenderWithFeature")) {
            // Intercept PendingIntent creation to ensure they use stub too
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof android.content.Intent[]) {
                    android.content.Intent[] intents = (android.content.Intent[]) args[i];
                    for (int j = 0; j < intents.length; j++) {
                        intents[j] = OneCoreStubManager.replaceWithStub(intents[j], sHostPackage);
                    }
                }
            }
        } else if (methodName.contains("startService") || methodName.contains("bindService")) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof android.content.Intent) {
                    android.content.Intent intent = (android.content.Intent) args[i];
                    if (intent.getComponent() != null && !intent.getComponent().getPackageName().equals(sHostPackage)) {
                        String guestPkg = intent.getComponent().getPackageName();
                        int procIndex = OneCoreProcessManager.getProcessIndex(guestPkg);
                        String stubClassName = "com.onecore.loader.StubService$P" + procIndex;
                        
                        android.content.Intent stubService = new android.content.Intent();
                        stubService.setClassName(sHostPackage, stubClassName);
                        stubService.putExtra("target_service", intent.getComponent().getClassName());
                        args[i] = stubService;
                    }
                }
            }
        } else if (methodName.equals("broadcastIntent") || methodName.equals("registerReceiver")) {
             // Spoof calling package for verification
             for (int i = 0; i < args.length; i++) {
                 if (args[i] instanceof String && args[i].equals(sHostPackage)) {
                     String targetPkg = VirtualContainer.getInstance().getPackageName();
                     if (targetPkg != null) args[i] = targetPkg;
                 }
                 if (args[i] instanceof android.content.Intent) {
                      android.content.Intent intent = (android.content.Intent) args[i];
                      // Fix action package names if they contain the virtual app's package
                 }
             }
        } else if (methodName.equals("getRunningAppProcesses")) {
            Object result = method.invoke(mBase, args);
            if (result instanceof java.util.List) {
                java.util.List list = (java.util.List) result;
                String targetPkg = VirtualContainer.getInstance().getPackageName();
                if (targetPkg == null) targetPkg = "com.pubg.imobile";
                
                for (Object item : list) {
                    try {
                        String procName = (String) ReflectionHelper.getFieldValue(item, "processName");
                        if (procName != null && (procName.contains(sHostPackage) || procName.contains(":virtual"))) {
                            ReflectionHelper.setFieldValue(item, targetPkg, "processName");
                        }
                    } catch (Exception ignored) {}
                }
            }
            return result;
        } else if (methodName.equals("getPackagesForUid")) {
            if (args != null && args.length > 0 && args[0] instanceof Integer) {
                int uid = (int) args[0];
                String targetPkg = VirtualContainer.getInstance().getPackageName();
                if (targetPkg != null) {
                    return new String[]{targetPkg};
                }
            }
        } else if (methodName.equals("getRunningServices")) {
            return new java.util.ArrayList<>(); // Hide running services
        } else if (methodName.equals("getContentProvider")) {
            // getContentProvider(callingThread, callingPackage, name, userId, stable)
            String authority = null;
            for (Object arg : args) {
                if (arg instanceof String && ((String)arg).contains(".")) {
                    authority = (String) arg;
                    break;
                }
            }
            if (authority != null) {
                if (OneCoreContentProviderManager.getProvider(authority) != null) {
                    Log.i(TAG, "AMS Proxy: Redirecting local provider request for " + authority);
                    // For now, let it proceed. If needed, we replace callingPackage with host.
                }
            }
        }
        return method.invoke(mBase, args);
    }
}
