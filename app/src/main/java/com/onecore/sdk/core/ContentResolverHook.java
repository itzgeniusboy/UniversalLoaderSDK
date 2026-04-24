package com.onecore.sdk.core;

import android.content.ContentProvider;
import android.os.Bundle;
import android.os.IBinder;
import com.onecore.sdk.utils.Logger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Proxies IContentProvider calls to our locally virtualized ContentProvider.
 */
public class ContentResolverHook implements InvocationHandler {
    private static final String TAG = "OneCore-CRHook";
    private final ContentProvider localProvider;

    public ContentResolverHook(ContentProvider localProvider) {
        this.localProvider = localProvider;
    }

    public static Object createProxy(ContentProvider localProvider) {
        try {
            Class<?> iContentProviderClass = Class.forName("android.content.IContentProvider");
            return Proxy.newProxyInstance(
                    localProvider.getClass().getClassLoader(),
                    new Class[]{iContentProviderClass, IBinder.class},
                    new ContentResolverHook(localProvider)
            );
        } catch (Exception e) {
            Logger.e(TAG, "Failed to create IContentProvider proxy", e);
            return null;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        Logger.v(TAG, "IContentProvider call: " + name);

        try {
            // Map common ContentProvider operations
            switch (name) {
                case "query":
                    // Handle different query signatures across Android versions
                    // args[0] is usually callingPkg (older) or attributionTag (newer) or Uri
                    return invokeLocal(name, args);
                case "insert":
                case "update":
                case "delete":
                case "bulkInsert":
                case "call":
                case "getType":
                case "openFile":
                case "openAssetFile":
                    return invokeLocal(name, args);
                
                // Binder specific methods
                case "asBinder":
                    return null; // Or a fake IBinder
                case "getInterfaceDescriptor":
                    return "android.content.IContentProvider";
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error in IContentProvider proxy: " + name, e);
        }

        return null;
    }

    private Object invokeLocal(String methodName, Object[] args) throws Exception {
        // signatures for IContentProvider differ significantly by API level
        // We use a flexible dispatcher that tries to match args to ContentProvider methods
        
        switch (methodName) {
            case "query":
                // Android 11+: query(attributionTag, uri, projection, queryArgs, cancellationSignal)
                // Android 10: query(callingPkg, uri, projection, queryArgs, cancellationSignal)
                // Older: query(callingPkg, uri, projection, selection, selectionArgs, sortOrder, cancellationSignal)
                return handleQuery(args);
            case "insert":
                // insert(callingPkg, attributionTag, uri, values, extras)
                return handleInsert(args);
            case "update":
                return handleUpdate(args);
            case "delete":
                return handleDelete(args);
            case "call":
                // call(callingPkg, attributionTag, authority, method, arg, extras)
                return handleCall(args);
            case "getType":
                return localProvider.getType(findUri(args));
        }
        
        return null; 
    }

    private android.net.Uri findUri(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof android.net.Uri) return (android.net.Uri);
        }
        return null;
    }

    private Object handleQuery(Object[] args) {
        android.net.Uri uri = findUri(args);
        // Simplification for brevity: extracting standard fields
        // In a full SDK we'd check API level and extract projection, selection, etc.
        return localProvider.query(uri, null, null, null, null);
    }

    private Object handleInsert(Object[] args) {
        android.net.Uri uri = findUri(args);
        android.content.ContentValues values = null;
        for (Object arg : args) if (arg instanceof android.content.ContentValues) values = (android.content.ContentValues) arg;
        return localProvider.insert(uri, values);
    }

    private Object handleUpdate(Object[] args) {
        android.net.Uri uri = findUri(args);
        android.content.ContentValues values = null;
        for (Object arg : args) if (arg instanceof android.content.ContentValues) values = (android.content.ContentValues) arg;
        return localProvider.update(uri, values, null, null);
    }

    private Object handleDelete(Object[] args) {
        return localProvider.delete(findUri(args), null, null);
    }

    private Object handleCall(Object[] args) {
        // call(callingPkg, attributionTag, authority, method, arg, extras)
        // Usually method starts from index 2 or 3
        String method = null;
        String argText = null;
        Bundle extras = null;
        
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) {
                if (method == null && i >= 2 && !((String)args[i]).contains(".")) method = (String) args[i];
                else if (method != null && argText == null) argText = (String) args[i];
            } else if (args[i] instanceof Bundle) {
                extras = (Bundle) args[i];
            }
        }
        return localProvider.call(method, argText, extras);
    }
}
