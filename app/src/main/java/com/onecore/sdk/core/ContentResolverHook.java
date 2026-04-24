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
                case "openTypedAssetFile":
                case "getStreamTypes":
                case "refresh":
                case "canonicalize":
                case "uncanonicalize":
                case "applyBatch":
                    return invokeLocal(name, args);
                
                // Binder specific methods
                case "asBinder":
                    return proxy; 
                case "getInterfaceDescriptor":
                    return "android.content.IContentProvider";
                case "pingBinder":
                    return true;
                case "isBinderAlive":
                    return true;
                case "queryLocalInterface":
                    return proxy;
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
            case "openFile":
                return localProvider.openFile(findUri(args), findString(args, "r"));
            case "openAssetFile":
                return localProvider.openAssetFile(findUri(args), findString(args, "r"));
            case "openTypedAssetFile":
                return localProvider.openTypedAssetFile(findUri(args), findString(args, "*/*"), findBundle(args), null);
            case "getStreamTypes":
                return localProvider.getStreamTypes(findUri(args), findString(args, "*/*"));
            case "bulkInsert":
                return handleBulkInsert(args);
            case "canonicalize":
                return localProvider.canonicalize(findUri(args));
            case "uncanonicalize":
                return localProvider.uncanonicalize(findUri(args));
            case "refresh":
                return localProvider.refresh(findUri(args), findBundle(args), null);
            case "applyBatch":
                return handleApplyBatch(args);
        }
        
        return null; 
    }

    private Object handleApplyBatch(Object[] args) {
        String authority = null;
        java.util.ArrayList<android.content.ContentProviderOperation> operations = null;
        for (Object arg : args) {
            if (arg instanceof String) authority = (String) arg;
            else if (arg instanceof java.util.ArrayList) operations = (java.util.ArrayList) arg;
        }
        try {
            return localProvider.applyBatch(operations);
        } catch (Exception e) {
            Logger.e(TAG, "Local applyBatch failed for " + authority, e);
            return null;
        }
    }

    private android.net.Uri findUri(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof android.net.Uri) return (android.net.Uri) arg;
        }
        return null;
    }

    private android.os.Bundle findBundle(Object[] args) {
        for (Object arg : args) if (arg instanceof android.os.Bundle) return (android.os.Bundle) arg;
        return null;
    }

    private String findString(Object[] args, String defaultValue) {
        for (Object arg : args) if (arg instanceof String) return (String) arg;
        return defaultValue;
    }

    private Object handleBulkInsert(Object[] args) {
        android.net.Uri uri = findUri(args);
        android.content.ContentValues[] values = null;
        for (Object arg : args) if (arg instanceof android.content.ContentValues[]) values = (android.content.ContentValues[]) arg;
        try {
            return localProvider.bulkInsert(uri, values);
        } catch (Exception e) {
            Logger.e(TAG, "Local bulkInsert failed: " + uri, e);
            return 0;
        }
    }

    private Object handleQuery(Object[] args) {
        android.net.Uri uri = findUri(args);
        String[] projection = null;
        String selection = null;
        String[] selectionArgs = null;
        String sortOrder = null;
        Bundle queryArgs = null;
        
        // Android 10+ uses queryArgs Bundle
        for (Object arg : args) {
            if (arg instanceof String[]) {
                if (projection == null) projection = (String[]) arg;
                else if (selectionArgs == null) selectionArgs = (String[]) arg;
            } else if (arg instanceof Bundle) {
                queryArgs = (Bundle) arg;
            } else if (arg instanceof String) {
                if (selection == null) selection = (String) arg;
                else if (sortOrder == null) sortOrder = (String) arg;
            }
        }
        
        try {
            if (queryArgs != null && android.os.Build.VERSION.SDK_INT >= 26) {
                return localProvider.query(uri, projection, queryArgs, null);
            }
            return localProvider.query(uri, projection, selection, selectionArgs, sortOrder);
        } catch (Exception e) {
            Logger.e(TAG, "Local query failed: " + uri, e);
            return null;
        }
    }

    private Object handleInsert(Object[] args) {
        android.net.Uri uri = findUri(args);
        android.content.ContentValues values = null;
        for (Object arg : args) {
            if (arg instanceof android.content.ContentValues) values = (android.content.ContentValues) arg;
        }
        return localProvider.insert(uri, values);
    }

    private Object handleUpdate(Object[] args) {
        android.net.Uri uri = findUri(args);
        android.content.ContentValues values = null;
        String selection = null;
        String[] selectionArgs = null;
        for (Object arg : args) {
            if (arg instanceof android.content.ContentValues) values = (android.content.ContentValues) arg;
            else if (arg instanceof String && selection == null && !((String)arg).contains(".")) selection = (String) arg;
            else if (arg instanceof String[]) selectionArgs = (String[]) arg;
        }
        return localProvider.update(uri, values, selection, selectionArgs);
    }

    private Object handleDelete(Object[] args) {
        android.net.Uri uri = findUri(args);
        String selection = null;
        String[] selectionArgs = null;
        for (Object arg : args) {
            if (arg instanceof String && selection == null) selection = (String) arg;
            else if (arg instanceof String[]) selectionArgs = (String[]) arg;
        }
        return localProvider.delete(uri, selection, selectionArgs);
    }

    private Object handleCall(Object[] args) {
        // call(callingPkg, attributionTag, authority, method, arg, extras)
        // Signatures vary significantly:
        // API 29+: call(callingPkg, attributionTag, authority, method, arg, extras)
        // API 19-28: call(callingPkg, method, arg, extras)
        
        String method = null;
        String argText = null;
        Bundle extras = null;
        
        for (Object arg : args) {
            if (arg instanceof Bundle) {
                extras = (Bundle) arg;
            }
        }
        
        // Find method and argText by heuristics or position
        if (args.length >= 4 && args[1] instanceof String && args[2] instanceof String) {
             // Likely API 19-28: callingPkg, method, arg, extras
             method = (String) args[1];
             argText = (String) args[2];
        } else if (args.length >= 6) {
             // Likely API 29+: callingPkg, attributionTag, authority, method, arg, extras
             method = (String) args[3];
             argText = (String) args[4];
        } else {
            // Heuristic fallback
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof String) {
                    String s = (String) args[i];
                    if (i >= 1 && method == null && !s.contains(".") && s.length() > 0) method = s;
                    else if (method != null && argText == null) argText = s;
                }
            }
        }
        
        try {
            return localProvider.call(method, argText, extras);
        } catch (Exception e) {
            Logger.e(TAG, "Local call failed: " + method, e);
            return null;
        }
    }
}
