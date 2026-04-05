package uz.shift.colorpicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class LineColorPicker extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] colors = new int[0];
    private int selectedIndex = -1;
    private OnColorChangedListener listener;

    public LineColorPicker(Context context) {
        super(context);
        init();
    }

    public LineColorPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineColorPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2));
        strokePaint.setColor(Color.WHITE);
    }

    public void setColors(int[] colors) {
        if (colors == null) {
            this.colors = new int[0];
            selectedIndex = -1;
        } else {
            this.colors = colors.clone();
            if (this.colors.length == 0) {
                selectedIndex = -1;
            } else if (selectedIndex < 0 || selectedIndex >= this.colors.length) {
                selectedIndex = 0;
            }
        }
        invalidate();
        notifyColorChanged();
    }

    public void setSelectedColor(int color) {
        if (colors.length == 0) {
            selectedIndex = -1;
            invalidate();
            return;
        }
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == color) {
                if (selectedIndex != i) {
                    selectedIndex = i;
                    invalidate();
                    notifyColorChanged();
                }
                return;
            }
        }
        selectedIndex = 0;
        invalidate();
        notifyColorChanged();
    }

    public int getColor() {
        if (colors.length == 0 || selectedIndex < 0 || selectedIndex >= colors.length) {
            return Color.TRANSPARENT;
        }
        return colors[selectedIndex];
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (colors.length == 0) {
            return;
        }
        float contentLeft = getPaddingLeft();
        float contentTop = getPaddingTop();
        float contentRight = getWidth() - getPaddingRight();
        float contentBottom = getHeight() - getPaddingBottom();
        float itemWidth = (contentRight - contentLeft) / colors.length;
        float radius = dp(4);
        for (int i = 0; i < colors.length; i++) {
            rect.set(contentLeft + i * itemWidth, contentTop, contentLeft + (i + 1) * itemWidth, contentBottom);
            fillPaint.setColor(colors[i]);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
            if (i == selectedIndex) {
                strokePaint.setColor(isLightColor(colors[i]) ? 0xFF212121 : Color.WHITE);
                canvas.drawRoundRect(rect, radius, radius, strokePaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (colors.length == 0) {
            return super.onTouchEvent(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                updateSelection(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateSelection(float x) {
        float contentLeft = getPaddingLeft();
        float contentRight = getWidth() - getPaddingRight();
        if (contentRight <= contentLeft) {
            return;
        }
        float itemWidth = (contentRight - contentLeft) / colors.length;
        int index = (int) ((x - contentLeft) / itemWidth);
        if (index < 0) {
            index = 0;
        } else if (index >= colors.length) {
            index = colors.length - 1;
        }
        if (selectedIndex != index) {
            selectedIndex = index;
            invalidate();
            notifyColorChanged();
        }
    }

    private void notifyColorChanged() {
        if (listener != null && selectedIndex >= 0 && selectedIndex < colors.length) {
            listener.onColorChanged(colors[selectedIndex]);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private boolean isLightColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return (red * 299 + green * 587 + blue * 114) / 1000 > 200;
    }
}
