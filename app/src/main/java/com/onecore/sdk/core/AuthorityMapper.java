package com.onecore.sdk.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps authorities to avoid conflicts and handle virtual redirection.
 */
public class AuthorityMapper {
    private static final Map<String, String> mAuthorityMap = new HashMap<>();

    public static void registerAuthority(String original, String virtual) {
        mAuthorityMap.put(original, virtual);
    }

    public static String getVirtualAuthority(String original) {
        String mapped = mAuthorityMap.get(original);
        return mapped != null ? mapped : original;
    }

    public static String getOriginalAuthority(String virtual) {
        for (Map.Entry<String, String> entry : mAuthorityMap.entrySet()) {
            if (entry.getValue().equals(virtual)) {
                return entry.getKey();
            }
        }
        return virtual;
    }
}
