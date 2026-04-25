package com.onecore.sdk.core.reflex;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstraction layer for reflection to handle changes in field/method names across Android versions.
 */
public class ReflectionHelper {
    private static final String TAG = "OneCore-Reflection";
    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new HashMap<>();

    public static Field findField(Class<?> clazz, String... names) {
        String cacheKey = clazz.getName() + "#" + String.join("|", names);
        if (FIELD_CACHE.containsKey(cacheKey)) return FIELD_CACHE.get(cacheKey);

        for (String name : names) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                FIELD_CACHE.put(cacheKey, field);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }

        // Search in hierarchy
        Class<?> current = clazz.getSuperclass();
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    FIELD_CACHE.put(cacheKey, field);
                    return field;
                } catch (NoSuchFieldException ignored) {}
            }
            current = current.getSuperclass();
        }

        Log.w(TAG, "Field not found in " + clazz.getName() + " for names: " + String.join(", ", names));
        return null;
    }

    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        String cacheKey = clazz.getName() + "#" + name;
        if (METHOD_CACHE.containsKey(cacheKey)) return METHOD_CACHE.get(cacheKey);

        try {
            Method method = clazz.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            METHOD_CACHE.put(cacheKey, method);
            return method;
        } catch (NoSuchMethodException e) {
            // Search in hierarchy
            Class<?> current = clazz.getSuperclass();
            while (current != null && current != Object.class) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    METHOD_CACHE.put(cacheKey, method);
                    return method;
                } catch (NoSuchMethodException ignored) {}
                current = current.getSuperclass();
            }
        }

        Log.w(TAG, "Method not found: " + clazz.getName() + "#" + name);
        return null;
    }

    public static Object getFieldValue(Object obj, String... names) {
        if (obj == null) return null;
        Field field = findField(obj.getClass(), names);
        if (field != null) {
            try {
                return field.get(obj);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Failed to get field: " + field.getName(), e);
            }
        }
        return null;
    }

    public static void setFieldValue(Object obj, Object value, String... names) {
        if (obj == null) return;
        Field field = findField(obj.getClass(), names);
        if (field != null) {
            try {
                field.set(obj, value);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Failed to set field: " + field.getName(), e);
            }
        }
    }

    public static Object invokeMethod(Object obj, String name, Object... args) {
        if (obj == null) return null;
        Class<?>[] argTypes = null;
        if (args != null && args.length > 0) {
            argTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
        }
        Method method = findMethod(obj.getClass(), name, argTypes);
        if (method != null) {
            try {
                return method.invoke(obj, args);
            } catch (Exception e) {
                Log.e(TAG, "Failed to invoke method: " + name, e);
            }
        }
        return null;
    }
}
