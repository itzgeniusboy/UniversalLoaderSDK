package com.onecore.sdk.utils;

import android.content.Context;
import java.io.InputStream;
import java.util.Scanner;

public class IOUtils {
    public static String readAssetFile(Context context, String fileName) {
        try {
            InputStream is = context.getAssets().open(fileName);
            Scanner scanner = new Scanner(is).useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } catch (Exception e) {
            Logger.e("IOUtils", "Failed to read asset: " + fileName, e);
            return null;
        }
    }
}
