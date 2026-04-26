package com.onecore.sdk.utils;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Enhanced Reflection Utility for OneCore SDK.
 * Supports recursive field searching, fallback field names, and static/instance handling.
 */
public class ReflectionHelper {
    private static final String TAG = "OneCore-Reflection";

    /**
     * Set a field value on a target. 
     * If target is a Class, it treats it as a static field on that class.
     * If target is null, it returns immediately.
     */
    public static void setFieldValue(Object target, Object value, String... fieldNames) {
        if (target == null || fieldNames == null || fieldNames.length == 0) return;

        Class<?> clazz = (target instanceof Class) ? (Class<?>) target : target.getClass();
        Object instance = (target instanceof Class) ? null : target;

        while (clazz != null && !clazz.equals(Object.class)) {
            for (String name : fieldNames) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(instance, value);
                    return; 
                } catch (NoSuchFieldException e) {
                    // Try next fallback
                } catch (Exception e) {
                    Log.e(TAG, "Failed to set field " + name + " on " + clazz.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Get a field value from a target.
     * If target is a Class, it treats it as a static field.
     */
    public static Object getFieldValue(Object target, String... fieldNames) {
        if (target == null || fieldNames == null || fieldNames.length == 0) return null;

        Class<?> clazz = (target instanceof Class) ? (Class<?>) target : target.getClass();
        Object instance = (target instanceof Class) ? null : target;

        while (clazz != null && !clazz.equals(Object.class)) {
            for (String name : fieldNames) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(instance);
                } catch (NoSuchFieldException e) {
                    // Try next
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get field " + name + " from " + clazz.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Special case for static fields where class name is provided as string.
     */
    public static Object getStaticFieldValue(String className, String... fieldNames) {
        try {
            return getFieldValue(Class.forName(className), fieldNames);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Invoke a method on a target.
     * If target is a Class, it treats it as a static method on that class if instance lookup fails.
     */
    public static Object invokeMethod(Object target, String methodName, Object... args) {
        if (methodName == null) return null;
        
        Class<?> clazz;
        Object instance;
        
        if (target == null) {
            // If target is null, we might be looking for a static method on ActivityThread
            // by default in our SDK logic.
            try {
                clazz = Class.forName("android.app.ActivityThread");
                instance = null;
            } catch (Exception e) {
                return null;
            }
        } else if (target instanceof Class) {
            clazz = (Class<?>) target;
            instance = null;
        } else {
            clazz = target.getClass();
            instance = target;
        }

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
                return method.invoke(instance, args);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to invoke method " + methodName + " on " + clazz.getName(), e);
        }
        return null;
    }

    /**
     * Helper for specific static method calls by class name string.
     */
    public static Object invokeMethod(String className, String methodName, Object... args) {
        try {
            return invokeMethod(Class.forName(className), methodName, args);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Invoke a static method on a class.
     */
    public static Object invokeStaticMethod(Class<?> clazz, String methodName, Object... args) {
        try {
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    m.setAccessible(true);
                    return m.invoke(null, args);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        Class<?> searchType = clazz;
        while (searchType != null) {
            Method[] methods = searchType.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(name)) {
                    if (parameterTypes == null || parameterTypesMatch(method.getParameterTypes(), parameterTypes)) {
                        return method;
                    }
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    private static boolean parameterTypesMatch(Class<?>[] declaredTypes, Class<?>[] passedTypes) {
        if (declaredTypes.length != passedTypes.length) return false;
        for (int i = 0; i < declaredTypes.length; i++) {
            if (passedTypes[i] == null || declaredTypes[i].isAssignableFrom(passedTypes[i])) {
                continue;
            }
            // Basic primitive handling
            if (declaredTypes[i].isPrimitive()) {
                if (declaredTypes[i] == int.class && passedTypes[i] == Integer.class) continue;
                if (declaredTypes[i] == boolean.class && passedTypes[i] == Boolean.class) continue;
                if (declaredTypes[i] == long.class && passedTypes[i] == Long.class) continue;
            }
            return false;
        }
        return true;
    }
}
