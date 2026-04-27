package com.onecore.sdk.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Handles extraction and loading of native .so libraries from the guest APK.
 */
public class OneCoreNativeLoader {
    private static final String TAG = "OneCore-NativeLoader";

    public static String copyNativeBinaries(Context context, String apkPath, String packageName) {
        try {
            File libDir = context.getDir("v_lib_" + packageName, Context.MODE_PRIVATE);
            if (!libDir.exists()) libDir.mkdirs();

            Log.i(TAG, "OneCore-DEBUG: Extracting native libraries to " + libDir.getAbsolutePath());
            
            ZipFile zipFile = new ZipFile(apkPath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            // Determine best ABI available in APK
            String bestAbi = null;
            String[] supportedAbis = android.os.Build.SUPPORTED_ABIS;
            
            // Priority list for Games (prefer 64-bit)
            java.util.List<String> priorityAbis = new java.util.ArrayList<>();
            priorityAbis.add("arm64-v8a");
            priorityAbis.add("armeabi-v7a");
            
            for (String abi : supportedAbis) {
                if (priorityAbis.contains(abi)) {
                    Enumeration<? extends ZipEntry> checkEntries = zipFile.entries();
                    while (checkEntries.hasMoreElements()) {
                        if (checkEntries.nextElement().getName().startsWith("lib/" + abi + "/")) {
                            bestAbi = abi;
                            break;
                        }
                    }
                }
                if (bestAbi != null) break;
            }
            
            if (bestAbi == null) bestAbi = "armeabi-v7a"; // Fallback
            
            Log.i(TAG, "OneCore-Native: Best APK ABI detected -> " + bestAbi);
            Log.i(TAG, "OneCore-Native: Device CPU ABIs -> " + java.util.Arrays.toString(supportedAbis));

            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (name.startsWith("lib/" + bestAbi + "/") && name.endsWith(".so")) {
                    String fileName = name.substring(name.lastIndexOf("/") + 1);
                    File outFile = new File(libDir, fileName);
                    
                    if (!outFile.exists() || outFile.length() != entry.getSize()) {
                        InputStream is = zipFile.getInputStream(entry);
                        FileOutputStream fos = new FileOutputStream(outFile);
                        byte[] buffer = new byte[16384]; // Larger buffer for speed
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                        is.close();
                        
                        // Critical: Ensure library is executable for the system linker
                        outFile.setExecutable(true, false);
                        outFile.setReadable(true, false);
                        outFile.setWritable(true, false);
                        
                        // Use shell if available for strict 755 (Runtime.exec is limited but can try)
                        try {
                            Runtime.getRuntime().exec("chmod 755 " + outFile.getAbsolutePath());
                        } catch (Exception ignored) {}

                        Log.d(TAG, "Extracted " + bestAbi + " lib: " + fileName);
                    }
                    count++;
                }
            }
            zipFile.close();
            Log.i(TAG, "OneCore-Native: Successfully extracted " + count + " libraries for " + bestAbi);
            android.util.Log.d(TAG, ">>> STEP 3: Native Libs Extracted <<<");

            // Pre-load common dependencies to stabilize the linker
            preloadKnownDependencies(libDir);

            // Try to set LD_LIBRARY_PATH for the process 
            // Note: This might not affect already loaded linker state but helps some apps
            try {
                String currentLd = System.getenv("LD_LIBRARY_PATH");
                String newLd = libDir.getAbsolutePath() + (currentLd != null ? ":" + currentLd : "");
                // Reflection to set environment variable if possible (unreliable on Android 10+)
                // Better approach is often handled via redirection in native code
                Log.d(TAG, "OneCore-Native: Suggested LD_LIBRARY_PATH: " + newLd);
            } catch (Exception ignored) {}

            // Initialize Native Hook Engine for the virtual app
            try {
                Log.i(TAG, "OneCore-Native: Loading onecore_native bridge...");
                System.loadLibrary("onecore_native");
                Log.i(TAG, "OneCore-Native: onecore_native bridge LOADED SUCCESS");
                android.util.Log.d(TAG, ">>> STEP 3.5: Native Bridge Loaded <<<");
                String vRoot = context.getDir("v_data_" + packageName, Context.MODE_PRIVATE).getAbsolutePath();
                Log.i(TAG, "OneCore-Native: Virtual Data Root -> " + vRoot);
                // Call the JNI method directly or via reflection if class is in another module
                try {
                     Class<?> hookMgr = Class.forName("com.onecore.sdk.NativeHookManager");
                     java.lang.reflect.Method init = hookMgr.getDeclaredMethod("initHooks", String.class, String.class);
                     init.invoke(null, vRoot, packageName);
                     Log.i(TAG, "OneCore-Native: Native Hook Engine initialized via NativeHookManager");
                } catch (Exception e) {
                     // Fallback to IORedirector if NativeHookManager missing
                     Class<?> ioRedir = Class.forName("com.onecore.sdk.IORedirector");
                     java.lang.reflect.Method init = ioRedir.getDeclaredMethod("initNativeHooks", String.class, String.class);
                     init.invoke(null, vRoot, packageName);
                     Log.i(TAG, "OneCore-Native: Native Hook Engine initialized via IORedirector");
                }
            } catch (Throwable t) {
                Log.e(TAG, "OneCore-Native: Native Hook Engine load FAILED", t);
            }
            
            return libDir.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract native libs", e);
            return null;
        }
    }

    private static void preloadKnownDependencies(File libDir) {
        String[] criticalLibs = {
            "libmain.so", "libunity.so", "libUE4.so", "libanort.so", "libTDataMaster.so"
        };
        
        for (String libName : criticalLibs) {
            File libFile = new File(libDir, libName);
            if (libFile.exists()) {
                try {
                    Log.i(TAG, "OneCore-Native: [Pre-load START] " + libName);
                    System.load(libFile.getAbsolutePath());
                    Log.i(TAG, "OneCore-Native: [Pre-load SUCCESS] " + libName);
                } catch (Throwable t) {
                    Log.w(TAG, "OneCore-Native: [Pre-load FAILED] " + libName + " | error=" + t.getMessage());
                }
            }
        }
    }
}
