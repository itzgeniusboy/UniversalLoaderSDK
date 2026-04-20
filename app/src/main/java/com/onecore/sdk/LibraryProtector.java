package com.onecore.sdk;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Handles AES-256 encryption and integrity checks for SDK libraries.
 */
public class LibraryProtector {
    private static final String ALGORITHM = "AES";
    private static final String KEY_SECRET = "onecore_lib_mask_256_bit_secure";

    public static byte[] decryptLibrary(byte[] encryptedData) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(KEY_SECRET.getBytes("UTF-8"));
        key = Arrays.copyOf(key, 32); // AES-256

        SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        return cipher.doFinal(encryptedData);
    }

    public static void encryptFile(File input, File output) throws Exception {
        byte[] data = new byte[(int) input.length()];
        try (FileInputStream fis = new FileInputStream(input)) {
            fis.read(data);
        }

        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(KEY_SECRET.getBytes("UTF-8"));
        key = Arrays.copyOf(key, 32);

        SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encrypted = cipher.doFinal(data);
        try (FileOutputStream fos = new FileOutputStream(output)) {
            fos.write(encrypted);
        }
    }

    public static boolean verifyChecksum(File file, String expectedHash) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int nread;
            while ((nread = fis.read(buffer)) != -1) {
                md.update(buffer, 0, nread);
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().equalsIgnoreCase(expectedHash);
        } catch (Exception e) {
            return false;
        }
    }
}
