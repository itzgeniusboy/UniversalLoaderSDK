# OneCore SDK ProGuard Rules
# Optimized for security and stealth

# Preserve JNI function names for NativeHook
-keepclasseswithmembernames class com.onecore.sdk.NativeHook {
    native <methods>;
}

# Keep the main SDK entry points
-keep class com.onecore.sdk.OneCoreSDK {
    public static void init(...);
    public static void install();
    public static void launchApp(...);
}

# Obfuscate all internal logic classes
-repackageclasses 'com.onecore.sdk.internal'
-allowaccessmodification

# Encrypt strings (requires commercial ProGuard or DexGuard usually, 
# but we handle basic string obfuscation in ObfuscationHelper)

# Prevent stripping of security checks
-keep class com.onecore.sdk.SecurityManager { *; }
-keep class com.onecore.sdk.AntiDump { *; }
-keep class com.onecore.sdk.AntiReverse { *; }

# General optimizations
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Remove logging in production
-assumenosideeffects class com.onecore.sdk.utils.Logger {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}
