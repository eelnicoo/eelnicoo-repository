package dev.nico.pulso;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * Raíz del panel. El cristal (fondo translúcido + desenfoque) lo pone la ventana;
 * aquí se pintan encima los brillos del color del modo y un reflejo arriba,
 * y se recorta todo a las esquinas redondeadas.
 */
final class PanelRoot extends FrameLayout {
    static final float RADIUS_DP = 28f;

    private final float radius = Ui.dpf(RADIUS_DP);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int c1, c2;
    private boolean mirror;
    private boolean dirty = true;
    private Shader g1, g2, g3;
    private ValueAnimator anim;

    PanelRoot(Context c) {
        super(c);
        setWillNotDraw(false);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
    }

    /** Panel abierto desde la derecha: los brillos van al lado contrario. */
    void setMirror(boolean m) {
        mirror = m;
        dirty = true;
        invalidate();
    }

    void setAccent(final int a, final int b, boolean animate) {
        if (anim != null) anim.cancel();
        if (!animate || c1 == 0) {
            c1 = a;
            c2 = b;
            dirty = true;
            invalidate();
            return;
        }
        final int f1 = c1, f2 = c2;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(450);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float t = (float) va.getAnimatedValue();
                c1 = Ui.mix(f1, a, t);
                c2 = Ui.mix(f2, b, t);
                dirty = true;
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        dirty = true;
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (dirty) {
            float big = Math.max(w, h);
            g1 = new RadialGradient(mirror ? w * 0.9f : w * 0.1f, 0f, big * 0.8f,
                    new int[] {Ui.alpha(c1, 0.30f), Ui.alpha(c1, 0.10f), 0},
                    new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            g2 = new RadialGradient(mirror ? w * 0.05f : w * 0.95f, h, big * 0.7f,
                    new int[] {Ui.alpha(c2, 0.18f), 0}, null, Shader.TileMode.CLAMP);
            g3 = new LinearGradient(0, 0, 0, Ui.dpf(56), 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            dirty = false;
        }
        glow.setShader(g1);
        cv.drawRect(0, 0, w, h, glow);
        glow.setShader(g2);
        cv.drawRect(0, 0, w, h, glow);
        glow.setShader(g3);
        cv.drawRect(0, 0, w, Ui.dpf(56), glow);
    }
}
