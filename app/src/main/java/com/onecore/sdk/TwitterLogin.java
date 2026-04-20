package com.onecore.sdk;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import com.onecore.sdk.utils.Logger;

/**
 * Twitter Login implementation for OneCore SDK Engine.
 * Attempts to open the Twitter app directly.
 */
public class TwitterLogin {
    private static final String TAG = "TwitterLogin";
    private static final String CALLBACK_SCHEME = "onecore.oauth://callback";

    public static void start(Activity activity, String key, String secret) {
        try {
            // Twitter Auth URL (simplified)
            String authUrl = "https://api.twitter.com/oauth/authenticate?oauth_token=MOCK_TOKEN";
            
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
            intent.setPackage("com.twitter.android"); // Force open Twitter app if installed
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                Logger.d(TAG, "Opening Twitter App...");
            } else {
                // Fallback to browser
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
                Logger.d(TAG, "Twitter App not found, opening Browser.");
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start Twitter login", e);
        }
    }

    public static String extractToken(String data) {
        // Extract token from onecore.oauth://callback?token=xxx
        if (data.contains("token=")) {
            return data.split("token=")[1].split("&")[0];
        }
        return "MOCK_TWITTER_TOKEN";
    }
}
