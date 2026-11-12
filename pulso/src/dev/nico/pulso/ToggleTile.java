package dev.nico.pulso;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

/** Botón cuadrado del panel (No molestar, HUD, captura…). Encendido = degradado del modo. */
final class ToggleTile extends View {
    private final Drawable icon;
    private final String label;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelP = Ui.paint(10f, Ui.TEXT2, Ui.SEMIBOLD);
    private final RectF r = new RectF();
    private final int heightPx;

    private boolean active;
    private boolean available = true;
    private int c1 = Mode.BALANCED.c1, c2 = Mode.BALANCED.c2;
    private String flash;
    private final Runnable clearFlash = new Runnable() {
        @Override
        public void run() {
            flash = null;
            invalidate();
        }
    };

    ToggleTile(Context c, int iconRes, String label, int heightDp) {
        super(c);
        this.icon = c.getDrawable(iconRes).mutate();
        this.label = label;
        heightPx = Ui.dp(heightDp);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, Ui.dpf(1)));
        labelP.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
        setContentDescription(label);
    }

    void setActive(boolean on) {
        if (active == on) return;
        active = on;
        invalidate();
    }

    boolean isActive() {
        return active;
    }

    /** false = necesita algo (Shizuku); se ve apagado con un punto ámbar. */
    void setAvailable(boolean ok) {
        if (available == ok) return;
        available = ok;
        invalidate();
    }

    boolean isAvailable() {
        return available;
    }

    void setAccent(int a, int b) {
        c1 = a;
        c2 = b;
        invalidate();
    }

    /** Muestra un texto un momento en lugar de la etiqueta (p. ej. «+420 MB»). */
    void flash(String text) {
        flash = text;
        removeCallbacks(clearFlash);
        if (text != null) postDelayed(clearFlash, 2600);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(90) : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, heightPx);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float radius = Ui.dpf(16);
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        int iconColor, textColor;
        if (active) {
            bg.setShader(new LinearGradient(0, 0, w, h, Ui.alpha(c1, 0.30f), Ui.alpha(c2, 0.16f), Shader.TileMode.CLAMP));
            stroke.setColor(Ui.alpha(c1, 0.65f));
            iconColor = Ui.TEXT;
            textColor = Ui.TEXT;
        } else {
            bg.setShader(null);
            bg.setColor(Ui.TILE);
            stroke.setColor(Ui.TILE_STROKE);
            iconColor = available ? Ui.TEXT2 : Ui.alpha(Ui.TEXT3, 0.8f);
            textColor = available ? Ui.TEXT2 : Ui.TEXT3;
        }
        cv.drawRoundRect(r, radius, radius, bg);
        cv.drawRoundRect(r, radius, radius, stroke);

        Ui.drawIcon(cv, icon, w / 2f, h * 0.40f, Ui.dpf(21), iconColor);
        labelP.setColor(flash != null ? Ui.TEXT : textColor);
        String text = flash != null ? flash : label;
        float avail = w - Ui.dpf(10), size = Ui.dpf(10);
        labelP.setTextSize(size);
        while (labelP.measureText(text) > avail && size > Ui.dpf(8.5f)) {
            size -= Ui.dpf(0.5f);
            labelP.setTextSize(size);
        }
        cv.drawText(Ui.fit(labelP, text, avail), w / 2f, h * 0.81f, labelP);

        if (active) {
            dot.setColor(c1);
            dot.setShadowLayer(Ui.dpf(4), 0, 0, Ui.alpha(c1, 0.9f));
            cv.drawCircle(w - Ui.dpf(11), Ui.dpf(11), Ui.dpf(2.5f), dot);
        } else if (!available) {
            dot.setColor(Ui.WARN);
            dot.clearShadowLayer();
            cv.drawCircle(w - Ui.dpf(11), Ui.dpf(11), Ui.dpf(2.5f), dot);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            animate().scaleX(1f).scaleY(1f).setDuration(160).start();
        }
        return super.onTouchEvent(e);
    }
}
