package com.loader.sdk;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.loader.sdk.utils.Logger;

/**
 * Floating Overlay Menu Service.
 * Provides in-game control for SDK features.
 */
public class FloatingMenu extends Service {
    private WindowManager windowManager;
    private LinearLayout menuLayout;
    private WindowManager.LayoutParams params;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupLayout();
    }

    private void setupLayout() {
        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackgroundColor(Color.parseColor("#CC000000"));
        menuLayout.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("UniversalLoader SDK");
        title.setTextColor(Color.GREEN);
        title.setGravity(Gravity.CENTER);
        menuLayout.addView(title);

        addButton("Hook Toggle", v -> Logger.d("Menu", "Hook toggled"));
        addButton("Spoof Device", v -> Logger.d("Menu", "Device spoof triggered"));
        addButton("Exit Menu", v -> stopSelf());

        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        menuLayout.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(menuLayout, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(menuLayout, params);
    }

    private void addButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setOnClickListener(listener);
        menuLayout.addView(btn);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (menuLayout != null) windowManager.removeView(menuLayout);
    }
}
