package com.onecore.sdk.core;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.util.Log;
import java.lang.reflect.Field;

/**
 * Utility to spoof app signatures.
 */
public class OneCoreSignatureProxy {
    private static final String TAG = "OneCore-Signature";

    public static void spoofSignature(PackageInfo info) {
        if (info == null) return;
        
        try {
            // In a real environment, we would read the actual signatures from the guest APK
            // For now, we reuse the host signature or a hardcoded one to satisfy basic checks
            Signature fakeSignature = new Signature("3082030d308201f5a003020102020448163f9b300d06092a864886f70d01010b0500");
            
            info.signatures = new Signature[]{fakeSignature};
            
            // For Android 9+ (SigningInfo)
            try {
                Class<?> signingInfoClass = Class.forName("android.content.pm.SigningInfo");
                Object signingInfo = signingInfoClass.getConstructor().newInstance();
                
                Field mSigningDetailsField = signingInfoClass.getDeclaredField("mSigningDetails");
                mSigningDetailsField.setAccessible(true);
                
                // This part is very version dependent, but let's try a common path
                Log.d(TAG, "SigningInfo spoofing attempted");
            } catch (Exception ignored) {}
            
            Log.i(TAG, "OneCore-DEBUG: Signature spoofed for " + info.packageName);
        } catch (Exception e) {
            Log.e(TAG, "Signature spoofing failed", e);
        }
    }
}
