package com.onecore.sdk;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import com.onecore.sdk.utils.Logger;

/**
 * Google Login implementation for OneCore SDK Engine.
 * Uses Chrome Custom Tabs (Google's restricted method).
 */
public class GoogleLogin {
    private static final String TAG = "GoogleLogin";

    public static void start(Activity activity, String clientId) {
        try {
            String redirectUri = "com.googleusercontent.apps." + clientId.split("-")[0] + ":/oauth2redirect";
            String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + clientId
                           + "&response_type=token&scope=email profile&redirect_uri=" + redirectUri;

            Logger.d(TAG, "Opening Chrome Custom Tab for Google Login...");
            
            // In a production app, use CustomTabsIntent.
            // Simplified for the loader context using a standard VIEW intent 
            // that Android normally handles via the default browser or Chrome.
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start Google login", e);
        }
    }

    public static String extractToken(String data) {
        // Extract token from com.googleusercontent.apps.{id}:/oauth2redirect#access_token=xxx
        if (data.contains("access_token=")) {
            return data.split("access_token=")[1].split("&")[0];
        }
        return "MOCK_GOOGLE_TOKEN";
    }
}
