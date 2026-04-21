package com.onecore.loader.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;

import androidx.appcompat.widget.AppCompatButton;

/**
 * iOS-style Pulsing Gradient Button.
 */
public class GradientButton extends AppCompatButton {
    private Paint paint;
    private RectF rectF;

    public GradientButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rectF = new RectF();
        setBackground(null); // Custom drawing
        setGravity(android.view.Gravity.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rectF.set(0, 0, getWidth(), getHeight());
        
        LinearGradient gradient = new LinearGradient(0, 0, getWidth(), getHeight(),
                0xFF39FF14, 0xFF006400, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        
        canvas.drawRoundRect(rectF, 80, 80, paint);
        super.onDraw(canvas);
    }

    public void startPulse() {
        ScaleAnimation pulse = new ScaleAnimation(1.0f, 1.05f, 1.0f, 1.05f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(800);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        startAnimation(pulse);
    }
}
