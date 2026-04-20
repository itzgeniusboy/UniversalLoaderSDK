package com.onecore.sdk;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import com.onecore.sdk.utils.Logger;

/**
 * Facebook Login implementation for OneCore SDK Engine.
 * Attempts to open the Facebook app directly.
 */
public class FacebookLogin {
    private static final String TAG = "FacebookLogin";

    public static void start(Activity activity, String appId) {
        try {
            // Facebook Auth URL
            String authUrl = "https://www.facebook.com/v12.0/dialog/oauth?client_id=" + appId 
                           + "&redirect_uri=fb" + appId + "://authorize";
            
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
            intent.setPackage("com.facebook.katana"); // Force open Facebook app
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                Logger.d(TAG, "Opening Facebook App...");
            } else {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
                Logger.d(TAG, "Facebook App not found, opening Browser.");
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start Facebook login", e);
        }
    }

    public static String extractToken(String data) {
        // Extract token from fb{app_id}://authorize#access_token=xxx
        if (data.contains("access_token=")) {
            return data.split("access_token=")[1].split("&")[0];
        }
        return "MOCK_FB_TOKEN";
    }
}
