package com.onecore.sdk;

import java.io.*;
import java.util.zip.*;
import com.onecore.sdk.utils.Logger;

/**
 * APK Patcher for OneCore SDK Engine.
 * Injects DEX and SO files into an existing APK.
 */
public class ApkPatcher {
    private static final String TAG = "ApkPatcher";

    /**
     * Patches an APK by injecting external DEX and native libraries.
     */
    public void patchApk(String originalPath, String outputPath, String loaderDexPath, String loaderSoPath) {
        Logger.d(TAG, "Starting APK Patch: " + originalPath);
        File originalApk = new File(originalPath);
        File patchedApk = new File(outputPath);

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(originalApk));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(patchedApk))) {

            ZipEntry entry;
            int dexIndex = 1;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                // Track max classesN.dex index
                if (name.startsWith("classes") && name.endsWith(".dex")) {
                    String numPart = name.replace("classes", "").replace(".dex", "");
                    if (!numPart.isEmpty()) {
                        dexIndex = Math.max(dexIndex, Integer.parseInt(numPart));
                    }
                }

                // Copy original entry
                zos.putNextEntry(new ZipEntry(name));
                copyStream(zis, zos);
                zos.closeEntry();
                zis.closeEntry();
            }

            // 1. Inject OneCore Loader DEX as the next classesN.dex
            String nextDexName = "classes" + (dexIndex + 1) + ".dex";
            injectFileToZip(zos, nextDexName, new File(loaderDexPath));
            Logger.d(TAG, "Injected DEX: " + nextDexName);

            // 2. Inject Native SO Loader
            if (loaderSoPath != null) {
                String soName = "lib/arm64-v8a/libonecore_loader.so";
                injectFileToZip(zos, soName, new File(loaderSoPath));
                Logger.d(TAG, "Injected SO: " + soName);
            }

            Logger.i(TAG, "APK Patching completed: " + outputPath);

        } catch (Exception e) {
            Logger.e(TAG, "APK Patching failed", e);
        }
    }

    private void injectFileToZip(ZipOutputStream zos, String entryName, File file) throws IOException {
        if (!file.exists()) return;
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file)) {
            copyStream(fis, zos);
        }
        zos.closeEntry();
    }

    private void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }
}
