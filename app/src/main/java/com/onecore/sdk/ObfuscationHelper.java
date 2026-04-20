package com.onecore.sdk;

import android.util.Base64;

/**
 * Provides runtime string obfuscation (XOR + Base64) for sensitive strings.
 * Prevents simple string searching in decompiled code.
 */
public class ObfuscationHelper {
    
    private static final String DEFAULT_KEY = "onecore_secret_key_2026";

    /**
     * Decrypts an obfuscated string using XOR and Base64.
     */
    public static String decrypt(String obfuscated) {
        return decrypt(obfuscated, DEFAULT_KEY);
    }

    public static String decrypt(String obfuscated, String key) {
        try {
            byte[] data = Base64.decode(obfuscated, Base64.DEFAULT);
            byte[] keyBytes = key.getBytes();
            byte[] result = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                result[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
            }
            return new String(result);
        } catch (Exception e) {
            return obfuscated; // Fail safe to original if it's not actually obfuscated
        }
    }

    /**
     * Helper to obfuscate a string (mostly used during development to generate constants).
     */
    public static String obfuscate(String original) {
        byte[] data = original.getBytes();
        byte[] keyBytes = DEFAULT_KEY.getBytes();
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }
}
