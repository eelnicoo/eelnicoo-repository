package dev.nico.folio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;

/** Miniatura de una página, con su número y el botón de quitar. */
final class PageCell extends View {
    final Page page;
    private final boolean chrome;
    private int number;
    private float shownRotation;
    private float targetRotation;
    private float thumbAlpha;
    private boolean lifted;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Icon close = new Icon(Icon.CLOSE, Ui.TEXT, 14, 2.6f);
    private final Icon alert = new Icon(Icon.ALERT, Ui.TEXT_3, 28);
    private final RectF rect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private Bitmap shaderBitmap;
    private BitmapShader shader;
    private ValueAnimator rotationAnim;

    PageCell(Context c, Page page, boolean chrome) {
        super(c);
        this.page = page;
        this.chrome = chrome;
        shownRotation = targetRotation = page.rotation;
        thumbAlpha = page.thumb != null ? 1f : 0f;
        bg.setColor(Ui.SURFACE_2);
        badge.setColor(0xE00B0B0E);
        badgeText.setColor(Ui.TEXT);
        badgeText.setTextSize(Ui.dp(12));
        badgeText.setTypeface(Ui.font(600));
        badgeText.setTextAlign(Paint.Align.CENTER);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(2));
        ring.setColor(Ui.ACCENT);
        final float radius = Ui.dp(chrome ? 14 : 20);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
    }

    void setNumber(int n) {
        if (n != number) {
            number = n;
            invalidate();
        }
    }

    void setLifted(boolean l) {
        lifted = l;
        invalidate();
    }

    void onThumbReady() {
        ValueAnimator a = ValueAnimator.ofFloat(thumbAlpha, 1f);
        a.setDuration(240);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                thumbAlpha = (Float) va.getAnimatedValue();
                invalidate();
            }
        });
        a.start();
    }

    /** Anima el giro hasta la rotación actual de la página, siempre en sentido horario. */
    void animateRotation() {
        float delta = ((page.rotation - targetRotation) % 360 + 360) % 360;
        targetRotation += delta;
        if (rotationAnim != null) rotationAnim.cancel();
        rotationAnim = ValueAnimator.ofFloat(shownRotation, targetRotation);
        rotationAnim.setDuration(280);
        rotationAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        rotationAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                shownRotation = (Float) va.getAnimatedValue();
                invalidate();
            }
        });
        rotationAnim.start();
    }

    /** ¿El toque (en coordenadas de la celda) cae sobre el botón de quitar? */
    boolean hitRemove(float x, float y) {
        if (!chrome) return false;
        float cx = getWidth() - Ui.dp(8) - Ui.dp(13);
        float cy = Ui.dp(8) + Ui.dp(13);
        float dx = x - cx, dy = y - cy;
        float r = Ui.dp(24);
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = Ui.dp(chrome ? 14 : 20);
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, r, r, bg);

        Bitmap b = page.thumb;
        if (b != null && !b.isRecycled() && thumbAlpha > 0f) {
            if (b != shaderBitmap) {
                shaderBitmap = b;
                shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
            float pad = Ui.dp(chrome ? 8 : 14);
            float aw = w - 2 * pad, ah = h - 2 * pad;
            float bw = b.getWidth(), bh = b.getHeight();
            double rad = Math.toRadians(shownRotation);
            float cos = (float) Math.abs(Math.cos(rad)), sin = (float) Math.abs(Math.sin(rad));
            float rw = bw * cos + bh * sin, rh = bw * sin + bh * cos;
            float scale = Math.min(aw / rw, ah / rh);
            shaderMatrix.setTranslate(-bw / 2f, -bh / 2f);
            shader.setLocalMatrix(shaderMatrix);
            img.setShader(shader);
            img.setAlpha(Math.round(255 * thumbAlpha));
            c.save();
            c.translate(w / 2f, h / 2f);
            c.rotate(shownRotation);
            c.scale(scale, scale);
            float ir = Ui.dp(6) / scale;
            rect.set(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f);
            c.drawRoundRect(rect, ir, ir, img);
            c.restore();
        } else if (page.failed) {
            int s = Ui.dp(28);
            alert.setBounds((int) (w - s) / 2, (int) (h - s) / 2, (int) (w + s) / 2, (int) (h + s) / 2);
            alert.draw(c);
        }

        if (chrome) {
            // Número de página.
            String n = String.valueOf(number);
            float bh = Ui.dp(22);
            float tw = badgeText.measureText(n);
            float bw = Math.max(bh, tw + Ui.dp(14));
            float left = Ui.dp(8), bottom = h - Ui.dp(8);
            rect.set(left, bottom - bh, left + bw, bottom);
            c.drawRoundRect(rect, bh / 2f, bh / 2f, badge);
            Paint.FontMetrics fm = badgeText.getFontMetrics();
            c.drawText(n, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, badgeText);

            // Botón de quitar.
            float cr = Ui.dp(13);
            float cx = w - Ui.dp(8) - cr, cy = Ui.dp(8) + cr;
            c.drawCircle(cx, cy, cr, badge);
            int is = Ui.dp(14);
            close.setBounds((int) (cx - is / 2f), (int) (cy - is / 2f), (int) (cx + is / 2f), (int) (cy + is / 2f));
            close.draw(c);
        }

        if (lifted) {
            float i = Ui.dp(1);
            rect.set(i, i, w - i, h - i);
            c.drawRoundRect(rect, r - i, r - i, ring);
        }
    }
}
