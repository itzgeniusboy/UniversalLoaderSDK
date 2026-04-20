package com.onecore.sdk;

import android.app.Activity;
import android.content.Intent;
import com.onecore.sdk.utils.Logger;

/**
 * Main helper for Social Logins in OneCore SDK Engine.
 * Supports Twitter, Facebook, and Google for cloned apps.
 */
public class SocialLoginHelper {
    private static final String TAG = "SocialLoginHelper";
    private static SocialLoginHelper instance;
    private String lastAccessToken;

    private SocialLoginHelper() {}

    public static synchronized SocialLoginHelper getInstance() {
        if (instance == null) {
            instance = new SocialLoginHelper();
        }
        return instance;
    }

    public void loginWithTwitter(Activity activity, String consumerKey, String consumerSecret) {
        Logger.d(TAG, "Initiating Twitter Login...");
        TwitterLogin.start(activity, consumerKey, consumerSecret);
    }

    public void loginWithFacebook(Activity activity, String appId) {
        Logger.d(TAG, "Initiating Facebook Login...");
        FacebookLogin.start(activity, appId);
    }

    public void loginWithGoogle(Activity activity, String clientId) {
        Logger.d(TAG, "Initiating Google Login...");
        GoogleLogin.start(activity, clientId);
    }

    /**
     * Handles the callback from social login intents.
     * @param intent The intent containing the result.
     * @return String result or null.
     */
    public String handleCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return null;
        String data = intent.getData().toString();
        Logger.d(TAG, "Received Social Login Callback: " + data);
        
        // Extract token based on scheme
        if (data.contains("onecore.oauth")) {
            lastAccessToken = TwitterLogin.extractToken(data);
        } else if (data.contains("fb")) {
            lastAccessToken = FacebookLogin.extractToken(data);
        } else if (data.contains("googleusercontent")) {
            lastAccessToken = GoogleLogin.extractToken(data);
        }
        
        return lastAccessToken;
    }

    public String getAccessToken() {
        return lastAccessToken;
    }
}
