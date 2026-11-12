package dev.nico.pulso;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Tarjeta de un dato: etiqueta, valor grande con unidad, una línea de detalle y una barra de progreso. */
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
        ringBg.setColor(0x17FFFFFF);
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
        float radius = Ui.dpf(16), pad = Ui.dpf(10);
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        cv.drawRoundRect(r, radius, radius, bg);
        cv.drawRoundRect(r, radius, radius, stroke);
        float avail = w - 2 * pad;

        // Etiqueta arriba.
        cv.drawText(Ui.fit(labelP, label, avail), pad, Ui.baseline(labelP, pad + Ui.dpf(5)), labelP);

        // Valor + unidad, encogiendo el valor si no cabe.
        float unitW = unit.isEmpty() ? 0f : unitP.measureText(unit) + Ui.dpf(3);
        float size = Ui.dpf(22);
        valueP.setTextSize(size);
        while (valueP.measureText(value) > avail - unitW && size > Ui.dpf(12)) {
            size -= Ui.dpf(1);
            valueP.setTextSize(size);
        }
        float base = h * 0.58f;
        cv.drawText(value, pad, base, valueP);
        if (!unit.isEmpty()) cv.drawText(unit, pad + valueP.measureText(value) + Ui.dpf(3), base, unitP);

        // Detalle.
        cv.drawText(Ui.fit(subP, sub, avail), pad, h - pad - Ui.dpf(9), subP);

        // Barra de progreso abajo.
        float by = h - Ui.dpf(9), bh = Ui.dpf(1.5f);
        arc.set(pad, by - bh, w - pad, by + bh);
        cv.drawRoundRect(arc, bh, bh, ringBg);
        if (shown > 0.005f) {
            ring.setColor(color);
            arc.set(pad, by - bh, pad + Math.max(2 * bh, avail * shown), by + bh);
            cv.drawRoundRect(arc, bh, bh, ring);
        }
    }
}
