package com.onecore.loader.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom view to show progress through the "Cloning -> Downloading -> Injecting -> Launching" steps.
 */
public class StepIndicator extends View {
    private Paint paint;
    private int currentStep = 0; // 0: Init, 1: Cloning, 2: Downloading, 3: Injecting, 4: Launching
    private final String[] stepNames = {"Perms", "Clone", "Download", "Inject", "Start"};

    public StepIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(24f);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    public void setStep(int step) {
        this.currentStep = step;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int margin = 60;
        int stepW = (w - (margin * 2)) / (stepNames.length - 1);

        for (int i = 0; i < stepNames.length; i++) {
            int cx = margin + (i * stepW);
            int cy = h / 3;

            // Draw connecting line
            if (i < stepNames.length - 1) {
                paint.setColor(i < currentStep ? Color.GREEN : Color.GRAY);
                paint.setStrokeWidth(8f);
                canvas.drawLine(cx, cy, cx + stepW, cy, paint);
            }

            // Draw node circle
            paint.setColor(i <= currentStep ? Color.GREEN : Color.GRAY);
            canvas.drawCircle(cx, cy, 15f, paint);

            // Draw label
            paint.setColor(i <= currentStep ? Color.WHITE : Color.GRAY);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(stepNames[i], cx, cy + 40, paint);
        }
    }
}
