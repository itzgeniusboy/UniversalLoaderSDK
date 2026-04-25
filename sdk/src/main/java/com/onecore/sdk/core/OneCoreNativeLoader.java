package com.onecore.sdk.core;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Handles native library extraction from APK.
 */
public class OneCoreNativeLoader {
    private static final String TAG = "OneCore-NativeLoader";

    public static void extract(String apkPath, File libDir) {
        try {
            Log.i(TAG, "OneCore-DEBUG: Extracting native libs to: " + libDir.getName());
            if (!libDir.exists()) libDir.mkdirs();
            
            ZipFile zipFile = new ZipFile(apkPath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    // Primitive ABI selection (prefer arm64-v8a if available, etc)
                    // For now, extract all but flat file names will collide. 
                    // Better: lib/<abi>/libname.so -> lib/libname.so
                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                    File outFile = new File(libDir, fileName);
                    
                    InputStream is = zipFile.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();
                }
            }
            zipFile.close();
        } catch (Exception e) {
            Log.e(TAG, "!!! OneCore-ERROR: Native extraction FAILED !!!", e);
        }
    }
}
