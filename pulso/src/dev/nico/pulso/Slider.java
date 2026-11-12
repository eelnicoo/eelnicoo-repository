package dev.nico.pulso;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Deslizador 0..1 con el degradado del modo. */
final class Slider extends View {
    interface Listener {
        void onChange(Slider s, float value, boolean done);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float value;
    private boolean dragging;
    private int c1 = Mode.BALANCED.c1, c2 = Mode.BALANCED.c2;
    private Listener listener;

    Slider(Context c) {
        super(c);
        track.setColor(0x1FFFFFFF);
        thumb.setColor(0xFFFFFFFF);
        thumb.setShadowLayer(Ui.dpf(3), 0, Ui.dpf(1), 0x66000000);
        setClickable(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setValue(float v) {
        value = Ui.clamp01(v);
        invalidate();
    }

    float value() {
        return value;
    }

    void setAccent(int a, int b) {
        c1 = a;
        c2 = b;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(240) : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Ui.dp(36));
    }

    private float x0() {
        return Ui.dpf(12);
    }

    private float x1() {
        return getWidth() - Ui.dpf(12);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float cy = getHeight() / 2f, x0 = x0(), x1 = x1();
        float x = x0 + (x1 - x0) * value;
        float th = Ui.dpf(2);
        cv.drawRoundRect(x0, cy - th, x1, cy + th, th, th, track);
        active.setShader(new LinearGradient(x0, 0, x1, 0, c1, c2, Shader.TileMode.CLAMP));
        cv.drawRoundRect(x0, cy - th, Math.max(x0 + 2 * th, x), cy + th, th, th, active);
        if (dragging) {
            halo.setColor(Ui.alpha(c1, 0.25f));
            cv.drawCircle(x, cy, Ui.dpf(17), halo);
        }
        cv.drawCircle(x, cy, Ui.dpf(10), thumb);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            dragging = true;
        }
        float x0 = x0(), x1 = x1();
        value = Ui.clamp01((e.getX() - x0) / Math.max(1f, x1 - x0));
        boolean done = a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL;
        if (done) dragging = false;
        if (listener != null) listener.onChange(this, value, done);
        invalidate();
        return true;
    }
}
