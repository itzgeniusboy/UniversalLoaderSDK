package com.onecore.sdk.core;

import android.util.Log;
import com.onecore.sdk.core.reflex.ReflectionHelper;
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
                    singleton = ReflectionHelper.getFieldValue(null, "IActivityTaskManagerSingleton");
                } catch (Exception ignored) {}
            }
            
            // Fallback to ActivityManager
            if (singleton == null) {
                Class<?> amClass = Class.forName("android.app.ActivityManager");
                singleton = ReflectionHelper.getFieldValue(null, "IActivityManagerSingleton", "gDefault");
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
        
        if (methodName.contains("startActivity")) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof android.content.Intent) {
                    android.content.Intent intent = (android.content.Intent) args[i];
                    android.content.Intent rewritten = OneCoreStubManager.replaceWithStub(intent, sHostPackage);
                    if (rewritten != intent) {
                        Log.d(TAG, "OneCore-DEBUG: IActivityManager.startActivity rewritten.");
                        args[i] = rewritten;
                    }
                    break;
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
        } else if (methodName.contains("scheduleJob")) {
            Log.d(TAG, "OneCore-DEBUG: JobScheduler intercepted.");
        } else if (methodName.contains("getContentProvider")) {
            // getContentProvider(callingThread, callingPackage, name, userId, stable)
            String authority = null;
            for (Object arg : args) {
                if (arg instanceof String) {
                    authority = (String) arg;
                    break;
                }
            }
            if (authority != null) {
                if (OneCoreContentProviderManager.getProvider(authority) != null) {
                    Log.i(TAG, "OneCore-DEBUG: High-level ContentProvider redirect for: " + authority);
                    // On modern Android, we'd return a ContentProviderHolder.
                    // However, if the provider is already installed in our process, 
                    // ActivityThread.acquireProvider might find it locally if we've registered it.
                }
            }
        }
        return method.invoke(mBase, args);
    }
}
