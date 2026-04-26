package com.onecore.sdk.utils;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Enhanced Reflection Utility for OneCore SDK.
 * Supports recursive field searching and fallback field names.
 */
public class ReflectionHelper {
    private static final String TAG = "OneCore-Reflection";

    /**
     * Set a field value on a target object, searching through classes and fallbacks.
     */
    public static void setFieldValue(Object target, Object value, String... fieldNames) {
        if (target == null || fieldNames == null || fieldNames.length == 0) return;

        Class<?> clazz = target.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            for (String name : fieldNames) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(target, value);
                    return; // Success
                } catch (NoSuchFieldException e) {
                    // Try next fallback name or move to superclass
                } catch (Exception e) {
                    Log.e(TAG, "Failed to set field " + name + " on " + target.getClass().getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Get a field value on a target object.
     */
    public static Object getFieldValue(Object target, String... fieldNames) {
        if (target == null || fieldNames == null || fieldNames.length == 0) return null;

        Class<?> clazz = target.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            for (String name : fieldNames) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException e) {
                    // Try next fallback
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get field " + name + " on " + target.getClass().getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Invoke a method on a target object.
     */
    public static Object invokeMethod(Object target, String methodName, Object... args) {
        if (target == null) return null;
        
        Class<?>[] argTypes = null;
        if (args != null && args.length > 0) {
            argTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
        }

        try {
            Method method = findMethod(target.getClass(), methodName, argTypes);
            if (method != null) {
                method.setAccessible(true);
                return method.invoke(target, args);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to invoke method " + methodName + " on " + target.getClass().getName(), e);
        }
        return null;
    }

    /**
     * Static version of invokeMethod for static methods.
     */
    public static Object invokeStaticMethod(Class<?> clazz, String methodName, Object... args) {
        Class<?>[] argTypes = null;
        if (args != null && args.length > 0) {
            argTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
        }

        try {
            Method method = findMethod(clazz, methodName, argTypes);
            if (method != null) {
                method.setAccessible(true);
                return method.invoke(null, args);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to invoke static method " + methodName + " on " + clazz.getName(), e);
        }
        return null;
    }

    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        Class<?> searchType = clazz;
        while (searchType != null) {
            Method[] methods = searchType.isInterface() ? searchType.getMethods() : searchType.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(name)) {
                    // Loose type matching for simplicity in SDK hooks
                    if (parameterTypes == null || method.getParameterTypes().length == parameterTypes.length) {
                        return method;
                    }
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }
}
