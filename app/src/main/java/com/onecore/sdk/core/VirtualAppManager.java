package com.onecore.sdk.core;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the list of apps added to the virtual container.
 */
public class VirtualAppManager {
    private static final String PREF_NAME = "virtual_apps";
    private static final String KEY_PKGS = "installed_pkgs";
    private static VirtualAppManager sInstance;
    private SharedPreferences mPrefs;

    private VirtualAppManager(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static VirtualAppManager get(Context context) {
        if (sInstance == null) sInstance = new VirtualAppManager(context.getApplicationContext());
        return sInstance;
    }

    public void addApp(String packageName) {
        Set<String> pkgs = getInstalledPackages();
        pkgs.add(packageName);
        mPrefs.edit().putStringSet(KEY_PKGS, pkgs).apply();
    }

    public Set<String> getInstalledPackages() {
        return new HashSet<>(mPrefs.getStringSet(KEY_PKGS, new HashSet<>()));
    }
}
