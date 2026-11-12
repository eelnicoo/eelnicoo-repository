package dev.nico.pulso;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Tarjeta de un dato: etiqueta, anillo de progreso, valor grande con unidad y una línea de detalle. */
final class MetricTile extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelP = Ui.paint(9.5f, Ui.TEXT2, Ui.SEMIBOLD);
    private final TextPaint valueP = Ui.paint(22f, Ui.TEXT, Ui.SEMIBOLD);
    private final TextPaint unitP = Ui.paint(11.5f, Ui.TEXT2, Ui.MEDIUM);
    private final TextPaint subP = Ui.paint(10.5f, Ui.TEXT3, Ui.MEDIUM);
    private final RectF r = new RectF();
    private final RectF arc = new RectF();
    private final int heightPx;

    private String label;
    private String value = "—";
    private String unit = "";
    private String sub = "";
    private int color = Ui.TEXT3;
    private float shown;
    private float target;
    private ValueAnimator anim;

    MetricTile(Context c, String label, int heightDp) {
        super(c);
        this.label = label;
        heightPx = Ui.dp(heightDp);
        bg.setColor(Ui.TILE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, Ui.dpf(1)));
        stroke.setColor(Ui.TILE_STROKE);
        ringBg.setStyle(Paint.Style.STROKE);
        ringBg.setStrokeWidth(Ui.dpf(3));
        ringBg.setColor(0x17FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dpf(3));
        ring.setStrokeCap(Paint.Cap.ROUND);
        labelP.setLetterSpacing(0.06f);
        valueP.setFontFeatureSettings("tnum");
        subP.setFontFeatureSettings("tnum");
    }

    void set(String label, String value, String unit, String sub, float frac, int color) {
        this.label = label;
        this.value = value;
        this.unit = unit;
        this.sub = sub;
        this.color = color;
        float f = Float.isNaN(frac) ? 0f : Ui.clamp01(frac);
        if (Math.abs(f - target) > 0.004f) {
            target = f;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(shown, f);
            anim.setDuration(500);
            anim.setInterpolator(new DecelerateInterpolator(1.6f));
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    shown = (float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }
        setContentDescription(label + " " + value + " " + unit);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(100) : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, heightPx);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float radius = Ui.dpf(16), pad = Ui.dpf(11);
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        cv.drawRoundRect(r, radius, radius, bg);
        cv.drawRoundRect(r, radius, radius, stroke);

        // Anillo arriba a la derecha.
        float rr = Ui.dpf(7f);
        float cx = w - pad - rr, cy = pad + rr;
        arc.set(cx - rr, cy - rr, cx + rr, cy + rr);
        cv.drawOval(arc, ringBg);
        if (shown > 0.005f) {
            ring.setColor(color);
            cv.drawArc(arc, -90f, 360f * shown, false, ring);
        }

        // Etiqueta.
        float labelW = cx - rr - Ui.dpf(6) - pad;
        cv.drawText(Ui.fit(labelP, label, labelW), pad, Ui.baseline(labelP, cy), labelP);

        // Valor + unidad, encogiendo el valor si no cabe.
        float avail = w - 2 * pad;
        float unitW = unit.isEmpty() ? 0f : unitP.measureText(unit) + Ui.dpf(3);
        float size = Ui.dpf(22);
        valueP.setTextSize(size);
        while (valueP.measureText(value) > avail - unitW && size > Ui.dpf(12)) {
            size -= Ui.dpf(1);
            valueP.setTextSize(size);
        }
        float base = h * 0.66f;
        cv.drawText(value, pad, base, valueP);
        if (!unit.isEmpty()) cv.drawText(unit, pad + valueP.measureText(value) + Ui.dpf(3), base, unitP);

        // Detalle.
        cv.drawText(Ui.fit(subP, sub, avail), pad, h - pad + Ui.dpf(1), subP);
    }
}
