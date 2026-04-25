package com.onecore.sdk.core;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NativeLibExtractor {
    private static final String TAG = "NativeLibExtractor";

    public static void extract(String apkPath, File libDir) {
        try {
            Log.i(TAG, "Extracting native libraries to: " + libDir.getAbsolutePath());
            if (!libDir.exists()) libDir.mkdirs();
            
            ZipFile zipFile = new ZipFile(apkPath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            // Simplified: extract everything under lib/ (needs better ABI filtering for production)
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    File outFile = new File(libDir, name.substring(name.lastIndexOf('/') + 1));
                    Log.d(TAG, "Extracting: " + name + " -> " + outFile.getName());
                    
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
            Log.e(TAG, "Failed to extract native libs", e);
        }
    }
}
