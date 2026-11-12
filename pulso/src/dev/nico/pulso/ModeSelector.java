package dev.nico.pulso;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Selector de los tres modos: una píldora con degradado y brillo se desliza al elegido. */
final class ModeSelector extends View {
    interface Listener {
        void onModeSelected(Mode m);
    }

    private final Mode[] modes;

    private final Drawable[] icons;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ind = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label;
    private final float labelSize;
    private final RectF r = new RectF();
    private final RectF ir = new RectF();
    private final int heightPx;

    private Mode mode = Mode.BALANCED;
    private float pos = 1f;
    private int c1 = Mode.BALANCED.c1, c2 = Mode.BALANCED.c2;
    private ValueAnimator anim;
    private Listener listener;

    ModeSelector(Context c, boolean compact, Mode[] modes) {
        super(c);
        this.modes = modes;
        this.icons = new Drawable[modes.length];
        heightPx = Ui.dp(compact ? 62 : 68);
        for (int i = 0; i < modes.length; i++) icons[i] = c.getDrawable(modes[i].icon).mutate();
        track.setColor(Ui.TILE);
        trackStroke.setStyle(Paint.Style.STROKE);
        trackStroke.setStrokeWidth(Math.max(1f, Ui.dpf(1)));
        trackStroke.setColor(Ui.TILE_STROKE);
        label = Ui.paint(compact ? 11f : 12.5f, Ui.TEXT2, Ui.SEMIBOLD);
        labelSize = Ui.dpf(compact ? 11f : 12.5f);
        label.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
        setContentDescription("Modo");
    }

    private int index(Mode m) {
        for (int i = 0; i < modes.length; i++) if (modes[i] == m) return i;
        return 0;
    }

    void setListener(Listener l) {
        listener = l;
    }

    Mode mode() {
        return mode;
    }

    void setMode(final Mode m, boolean animate) {
        if (anim != null) anim.cancel();
        mode = m;
        if (!animate || !isAttachedToWindow()) {
            pos = index(m);
            c1 = m.c1;
            c2 = m.c2;
            invalidate();
            return;
        }
        final float from = pos;
        final int f1 = c1, f2 = c2;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(360);
        anim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (float) a.getAnimatedValue();
                pos = from + (index(m) - from) * t;
                c1 = Ui.mix(f1, m.c1, t);
                c2 = Ui.mix(f2, m.c2, t);
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(300) : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, heightPx);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float radius = Ui.dpf(18), p = Ui.dpf(4);
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        cv.drawRoundRect(r, radius, radius, track);
        cv.drawRoundRect(r, radius, radius, trackStroke);

        float seg = (w - 2 * p) / modes.length;
        ir.set(p + pos * seg, p, p + pos * seg + seg, h - p);
        float ri = radius - p;
        ind.setShader(new LinearGradient(ir.left, ir.top, ir.right, ir.bottom, c1, c2, Shader.TileMode.CLAMP));
        ind.setShadowLayer(Ui.dpf(12), 0, Ui.dpf(3), Ui.alpha(c1, 0.55f));
        cv.drawRoundRect(ir, ri, ri, ind);
        gloss.setShader(new LinearGradient(0, ir.top, 0, ir.centerY(), 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRoundRect(ir, ri, ri, gloss);

        float iconSize = Ui.dpf(20);
        for (int i = 0; i < modes.length; i++) {
            float sel = Math.max(0f, 1f - Math.abs(pos - i));
            int col = Ui.mix(Ui.TEXT2, Ui.INK, sel);
            float cx = p + seg * i + seg / 2f;
            Ui.drawIcon(cv, icons[i], cx, h * 0.39f, iconSize, col);
            label.setColor(col);
            label.setTypeface(sel > 0.5f ? Ui.BOLD : Ui.SEMIBOLD);
            float avail = seg - Ui.dpf(6), size = labelSize;
            label.setTextSize(size);
            while (label.measureText(modes[i].label) > avail && size > Ui.dpf(8.5f)) {
                size -= Ui.dpf(0.5f);
                label.setTextSize(size);
            }
            cv.drawText(Ui.fit(label, modes[i].label, avail), cx, h * 0.80f, label);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_UP) {
            float p = Ui.dpf(4);
            float seg = (getWidth() - 2 * p) / modes.length;
            int i = (int) ((e.getX() - p) / seg);
            i = Math.max(0, Math.min(modes.length - 1, i));
            if (modes[i] != mode) {
                setMode(modes[i], true);
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                if (listener != null) listener.onModeSelected(mode);
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
