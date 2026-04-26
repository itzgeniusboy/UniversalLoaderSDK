package com.onecore.sdk.core;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;
import com.onecore.sdk.utils.ReflectionHelper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxies the DisplayManager service to ensure virtual apps get correct display info.
 */
public class OneCoreDisplayProxy implements InvocationHandler {
    private static final String TAG = "OneCore-DisplayProxy";
    private final Object mBase;

    public static void install() {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = smClass.getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getServiceMethod.invoke(null, Context.DISPLAY_SERVICE);

            if (binder == null) return;

            Class<?> stubClass = Class.forName("android.hardware.display.IDisplayManager$Stub");
            Method asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            Object base = asInterfaceMethod.invoke(null, binder);

            Object proxy = Proxy.newProxyInstance(
                base.getClass().getClassLoader(),
                new Class[]{Class.forName("android.hardware.display.IDisplayManager")},
                new OneCoreDisplayProxy(base)
            );

            // Replace in ServiceManager cache if possible (Android caches these in some versions)
            // But usually, ActivityThread and ContextImpl get them via ServiceManager.
            // A more reliable way is to hook the DisplayManagerGlobal.
            
            try {
                Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
                Object dmg = ReflectionHelper.invokeStaticMethod(dmgClass, "getInstance");
                if (dmg != null) {
                    ReflectionHelper.setFieldValue(dmg, proxy, "mDm");
                    Log.i(TAG, "OneCore-DEBUG: DisplayManagerGlobal successfully hooked.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to hook DisplayManagerGlobal directly");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to install DisplayProxy", e);
        }
    }

    private OneCoreDisplayProxy(Object base) {
        this.mBase = base;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        
        if (name.equals("getDisplayInfo")) {
            // Force primary display (0) info
            if (args != null && args.length > 0) {
                args[0] = 0; // Set displayId to 0
            }
            Log.d(TAG, "Intercepted getDisplayInfo -> Forced Display 0");
        } else if (name.equals("getDisplayIds")) {
            // Always include display 0
            return new int[]{0};
        } else if (name.equals("getDisplay")) {
            // Android 12+ display handling: Ensure display 0 is used
            if (args != null && args.length > 0) {
                args[0] = 0; 
            }
            Log.d(TAG, "Intercepted getDisplay -> Forced Display 0");
        }
        
        try {
            return method.invoke(mBase, args);
        } catch (Throwable t) {
            throw t.getCause() != null ? t.getCause() : t;
        }
    }
}
