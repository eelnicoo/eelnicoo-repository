package dev.nico.pulso;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Fondo de la app: casi negro con dos brillos del color del modo. */
final class AmbientView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int c1, c2;
    private boolean dirty = true;
    private Shader g1, g2;
    private ValueAnimator anim;

    AmbientView(Context c) {
        super(c);
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
        anim.setDuration(600);
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
        dirty = true;
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        cv.drawColor(Ui.BG);
        if (w == 0 || h == 0) return;
        if (dirty) {
            g1 = new RadialGradient(w * 0.12f, -h * 0.02f, w * 1.15f,
                    new int[] {Ui.alpha(c1, 0.26f), Ui.alpha(c1, 0.07f), 0}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            g2 = new RadialGradient(w * 1.05f, h * 0.42f, w * 0.95f,
                    new int[] {Ui.alpha(c2, 0.15f), 0}, null, Shader.TileMode.CLAMP);
            dirty = false;
        }
        paint.setShader(g1);
        cv.drawRect(0, 0, w, h, paint);
        paint.setShader(g2);
        cv.drawRect(0, 0, w, h, paint);
    }
}
