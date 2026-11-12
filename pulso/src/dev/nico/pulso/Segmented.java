package dev.nico.pulso;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Selector de texto (Izquierda | Derecha…) con un indicador que se desliza. */
final class Segmented extends View {
    interface Listener {
        void onSelect(Segmented s, int index);
    }

    private final String[] items;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ind = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = Ui.paint(13f, Ui.TEXT2, Ui.SEMIBOLD);
    private final RectF r = new RectF();
    private final RectF ir = new RectF();
    private int index;
    private float pos;
    private ValueAnimator anim;
    private Listener listener;

    Segmented(Context c, String... items) {
        super(c);
        this.items = items;
        track.setColor(Ui.TILE);
        trackStroke.setStyle(Paint.Style.STROKE);
        trackStroke.setStrokeWidth(Math.max(1f, Ui.dpf(1)));
        trackStroke.setColor(Ui.TILE_STROKE);
        ind.setColor(0x21FFFFFF);
        indStroke.setStyle(Paint.Style.STROKE);
        indStroke.setStrokeWidth(Math.max(1f, Ui.dpf(1)));
        indStroke.setColor(0x1CFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void select(int i, boolean animate) {
        index = Math.max(0, Math.min(items.length - 1, i));
        if (anim != null) anim.cancel();
        if (!animate || !isAttachedToWindow()) {
            pos = index;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, index);
        anim.setDuration(280);
        anim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                pos = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(260) : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Ui.dp(42));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), p = Ui.dpf(3.5f), rad = Ui.dpf(14);
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        cv.drawRoundRect(r, rad, rad, track);
        cv.drawRoundRect(r, rad, rad, trackStroke);
        float seg = (w - 2 * p) / items.length;
        ir.set(p + pos * seg, p, p + pos * seg + seg, h - p);
        cv.drawRoundRect(ir, rad - p, rad - p, ind);
        cv.drawRoundRect(ir, rad - p, rad - p, indStroke);
        for (int i = 0; i < items.length; i++) {
            float sel = Math.max(0f, 1f - Math.abs(pos - i));
            text.setColor(Ui.mix(Ui.TEXT2, Ui.TEXT, sel));
            float cx = p + seg * i + seg / 2f;
            cv.drawText(Ui.fit(text, items[i], seg - Ui.dpf(8)), cx, Ui.baseline(text, h / 2f), text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            float p = Ui.dpf(3.5f);
            float seg = (getWidth() - 2 * p) / items.length;
            int i = Math.max(0, Math.min(items.length - 1, (int) ((e.getX() - p) / seg)));
            if (i != index) {
                select(i, true);
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                if (listener != null) listener.onSelect(this, i);
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
