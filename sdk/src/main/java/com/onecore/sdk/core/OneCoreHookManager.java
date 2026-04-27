package com.onecore.sdk.core;

import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Hook Manager for OneCore.
 * Provides sophisticated hooking strategies including signature analysis, 
 * pattern matching, and exception-based resilience for diverse Android versions.
 */
public class OneCoreHookManager {
    private static final String TAG = "OneCore-HookMgr";

    /**
     * Sophisticated signature analysis to identify methods even when obfuscated.
     * Matches by return type, parameter types, and optional modifiers.
     */
    public static Method findMethodBySignature(Class<?> clazz, Class<?> returnType, int requiredModifiers, Class<?>... parameterTypes) {
        for (Method method : clazz.getDeclaredMethods()) {
            if ((method.getModifiers() & requiredModifiers) == requiredModifiers &&
                method.getReturnType().equals(returnType) &&
                Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                method.setAccessible(true);
                return method;
            }
        }
        
        // Recursive search in hierarchy
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return findMethodBySignature(superClass, returnType, requiredModifiers, parameterTypes);
        }
        
        return null;
    }

    /**
     * Overload for common case without strict modifier requirements.
     */
    public static Method findMethodBySignature(Class<?> clazz, Class<?> returnType, Class<?>... parameterTypes) {
        return findMethodBySignature(clazz, returnType, 0, parameterTypes);
    }

    /**
     * Exception-based resilient hook applicator.
     * Successively tries provided strategies until one succeeds.
     */
    public static void applyResilientHook(String hookName, HookTask primary, HookTask... fallbacks) {
        try {
            Log.d(TAG, "Attempting primary strategy for: " + hookName);
            primary.execute();
            Log.i(TAG, "Primary hook strategy successful: " + hookName);
        } catch (Throwable e) {
            Log.w(TAG, "Primary strategy failed for " + hookName + " (" + e.getMessage() + "). Initiating exception-based fallback sequence...");
            
            boolean resolved = false;
            for (int i = 0; i < fallbacks.length; i++) {
                try {
                    Log.d(TAG, "Executing fallback strategy #" + (i + 1) + " for: " + hookName);
                    fallbacks[i].execute();
                    Log.i(TAG, "Fallback strategy #" + (i + 1) + " successful for: " + hookName);
                    resolved = true;
                    break;
                } catch (Throwable fe) {
                    Log.w(TAG, "Fallback strategy #" + (i + 1) + " failed: " + fe.getMessage());
                }
            }
            
            if (!resolved) {
                Log.e(TAG, "FATAL: All hooking strategies failed for " + hookName + ". System stability may be compromised.");
            }
        }
    }

    /**
     * Functional interface for hooking tasks that can throw exceptions.
     */
    public interface HookTask {
        void execute() throws Throwable;
    }

    /**
     * Locates a method using a fuzzy pattern of parameter types.
     * Useful when additional parameters are added in newer Android versions.
     */
    public static List<Method> findMethodsByParameterPattern(Class<?> clazz, Class<?>... partialParams) {
        List<Method> matches = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] actualParams = method.getParameterTypes();
            if (containsPattern(actualParams, partialParams)) {
                method.setAccessible(true);
                matches.add(method);
            }
        }
        return matches;
    }

    private static boolean containsPattern(Class<?>[] actual, Class<?>[] pattern) {
        if (pattern.length > actual.length) return false;
        
        // Simple subsequence matching
        int p = 0;
        for (int i = 0; i < actual.length && p < pattern.length; i++) {
            if (actual[i].equals(pattern[p])) {
                p++;
            }
        }
        return p == pattern.length;
    }

    /**
     * Advanced: Scans for methods that could be "wrappers" (same return, same param, just one extra).
     */
    public static Method findProbableWrapper(Class<?> clazz, Method original) {
        Class<?>[] originalParams = original.getParameterTypes();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(original.getName())) {
                Class<?>[] currentParams = method.getParameterTypes();
                if (currentParams.length == originalParams.length + 1) {
                    // Possible wrapper with extra parameter (like user ID or attribution tag)
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }
}
