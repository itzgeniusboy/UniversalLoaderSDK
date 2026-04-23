package com.onecore.loader;

import android.app.Activity;
import android.os.Bundle;

/**
 * Settings screen for OneCore Loader.
 */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> {
            new UpdateChecker(this).checkForUpdates(true);
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }
}
