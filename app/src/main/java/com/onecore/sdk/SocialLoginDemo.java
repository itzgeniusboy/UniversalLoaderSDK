package com.onecore.sdk;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.onecore.sdk.utils.Logger;

/**
 * Testing Activity for OneCore SDK Engine Social Login.
 */
public class SocialLoginDemo extends Activity {
    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("Social Login Test");
        title.setTextSize(20);
        layout.addView(title);

        resultText = new TextView(this);
        resultText.setText("Status: Not logged in");
        layout.addView(resultText);

        addButton(layout, "Login with Twitter", v -> {
            SocialLoginHelper.getInstance().loginWithTwitter(this, "KEY", "SECRET");
        });

        addButton(layout, "Login with Facebook", v -> {
            SocialLoginHelper.getInstance().loginWithFacebook(this, "APP_ID");
        });

        addButton(layout, "Login with Google", v -> {
            SocialLoginHelper.getInstance().loginWithGoogle(this, "CLIENT_ID.apps.googleusercontent.com");
        });

        setContentView(layout);
        
        // Handle incoming intent if redirected back
        if (getIntent() != null && getIntent().getData() != null) {
            String token = SocialLoginHelper.getInstance().handleCallback(getIntent());
            if (token != null) {
                resultText.setText("Login Success: " + token);
            }
        }
    }

    private void addButton(LinearLayout layout, String text, android.view.View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setOnClickListener(listener);
        layout.addView(btn);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String token = SocialLoginHelper.getInstance().handleCallback(intent);
        if (token != null) {
            resultText.setText("Login Success Callback: " + token);
        }
    }
}
