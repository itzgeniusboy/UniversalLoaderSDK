package com.onecore.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Dialog shown when SDK license is expired.
 */
public class ExpiryDialog {

    public static void show(final Activity activity, String expiryDate) {
        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 50, 50, 50);
            layout.setGravity(Gravity.CENTER);

            TextView title = new TextView(activity);
            title.setText("⚠️ LICENSE EXPIRED");
            title.setTextSize(22);
            title.setTextColor(Color.RED);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);

            TextView message = new TextView(activity);
            message.setText("Your OneCore SDK Engine license expired on " + (expiryDate != null ? expiryDate : "an unknown date") + ".\nPlease contact your administrator to renew.");
            message.setTextSize(16);
            message.setGravity(Gravity.CENTER);
            message.setPadding(0, 0, 0, 40);

            layout.addView(title);
            layout.addView(message);

            builder.setView(layout);
            builder.setCancelable(false);
            builder.setPositiveButton("OK", (dialog, which) -> {
                activity.finish();
                System.exit(0);
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }
}
