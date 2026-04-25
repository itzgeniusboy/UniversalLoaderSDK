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
            
            // Determine primary ABI (high priority for Gaming)
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            String primaryAbi = abis.length > 0 ? abis[0] : "armeabi-v7a";
            
            Log.i(TAG, "OneCore-DEBUG: Target ABI for Gaming -> " + primaryAbi);

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    // Optimized extraction: Match best ABI or fallback to v7a
                    boolean match = false;
                    for (String abi : abis) {
                        if (name.contains(abi)) {
                            match = true;
                            break;
                        }
                    }

                    if (match) {
                        String fileName = name.substring(name.lastIndexOf("/") + 1);
                        File outFile = new File(libDir, fileName);
                        
                        if (!outFile.exists() || outFile.length() != entry.getSize()) {
                            InputStream is = zipFile.getInputStream(entry);
                            FileOutputStream fos = new FileOutputStream(outFile);
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                            fos.close();
                            is.close();
                            Log.d(TAG, "Extracted: " + fileName);
                        }
                    }
                }
            }
            zipFile.close();
            return libDir.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract native libs", e);
            return null;
        }
    }
}
