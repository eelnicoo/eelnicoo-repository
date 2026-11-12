package dev.nico.pulso;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Interruptor con el degradado del modo. */
final class ToggleSwitch extends View {
    interface Listener {
        void onChanged(ToggleSwitch s, boolean on);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean on;
    private float t;
    private int c1 = Mode.BALANCED.c1, c2 = Mode.BALANCED.c2;
    private Listener listener;
    private ValueAnimator anim;

    ToggleSwitch(Context c) {
        super(c);
        knob.setShadowLayer(Ui.dpf(2.5f), 0, Ui.dpf(1), 0x66000000);
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setChecked(!on, true);
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                if (listener != null) listener.onChanged(ToggleSwitch.this, on);
            }
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isChecked() {
        return on;
    }

    void setAccent(int a, int b) {
        c1 = a;
        c2 = b;
        invalidate();
    }

    void setChecked(boolean v, boolean animate) {
        on = v;
        if (anim != null) anim.cancel();
        float to = v ? 1f : 0f;
        if (!animate || !isAttachedToWindow()) {
            t = to;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(t, to);
        anim.setDuration(220);
        anim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                t = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Ui.dp(50), Ui.dp(30));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        r.set(Ui.dpf(1), Ui.dpf(2), w - Ui.dpf(1), h - Ui.dpf(2));
        float rad = r.height() / 2f;
        track.setShader(null);
        track.setColor(0x26FFFFFF);
        cv.drawRoundRect(r, rad, rad, track);
        if (t > 0.01f) {
            track.setShader(new LinearGradient(r.left, 0, r.right, 0, c1, c2, Shader.TileMode.CLAMP));
            track.setAlpha(Math.round(255 * t));
            cv.drawRoundRect(r, rad, rad, track);
            track.setAlpha(255);
        }
        float kr = rad - Ui.dpf(3);
        float x0 = r.left + Ui.dpf(3) + kr, x1 = r.right - Ui.dpf(3) - kr;
        knob.setColor(Ui.mix(0xFFC5CAD4, 0xFFFFFFFF, t));
        cv.drawCircle(x0 + (x1 - x0) * t, r.centerY(), kr, knob);
    }
}
