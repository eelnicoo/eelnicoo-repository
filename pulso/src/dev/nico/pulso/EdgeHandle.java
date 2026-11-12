package dev.nico.pulso;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import java.util.Collections;

/**
 * El tirador: una franja fina en el borde con una píldora luminosa.
 * Deslizar hacia dentro abre el panel; mantener pulsado y arrastrar lo mueve.
 * Su ventana, siempre visible, también lleva «no apagar la pantalla» y,
 * sin Shizuku, la frecuencia de pantalla preferida del modo.
 */
final class EdgeHandle {
    interface Callback {
        void onOpenPanel();
    }

    private final Context ctx;
    private final WindowManager wm;
    private final Callback cb;
    private final HandleView view;
    private final WindowManager.LayoutParams lp;
    private boolean attached;

    EdgeHandle(Context wctx, Callback cb) {
        ctx = wctx;
        wm = wctx.getSystemService(WindowManager.class);
        this.cb = cb;
        view = new HandleView(wctx);
        lp = new WindowManager.LayoutParams(Ui.dp(22), Ui.dp(120),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("Pulso:tirador");
    }

    void attach() {
        if (attached) return;
        layout();
        try {
            wm.addView(view, lp);
            attached = true;
        } catch (RuntimeException e) {
            Log.w("Pulso", "No se pudo añadir el tirador", e);
        }
    }

    void detach() {
        if (!attached) return;
        try {
            wm.removeViewImmediate(view);
        } catch (RuntimeException ignored) {
        }
        attached = false;
    }

    void relayout() {
        layout();
        push();
    }

    void setKeepScreenOn(boolean on) {
        if (on) lp.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        else lp.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        push();
    }

    /** Modo de pantalla preferido (0 = el que quiera el sistema). */
    void setRefreshHint(int modeId, float hz) {
        lp.preferredDisplayModeId = modeId;
        lp.preferredRefreshRate = modeId == 0 ? 0f : hz;
        push();
    }

    void setAccent(int color) {
        view.accent = color;
        view.invalidate();
    }

    private void push() {
        if (!attached) return;
        try {
            wm.updateViewLayout(view, lp);
        } catch (RuntimeException ignored) {
        }
    }

    private Rect bounds() {
        return wm.getCurrentWindowMetrics().getBounds();
    }

    private void layout() {
        Rect b = bounds();
        int side = Prefs.side(ctx);
        int w = Ui.dp(22);
        int h = Math.min(Ui.dp(Prefs.size(ctx)), b.height() - Ui.dp(48));
        lp.width = w;
        lp.height = h;
        lp.x = side == 0 ? 0 : b.width() - w;
        int cy = Math.round(Prefs.pos(ctx) * b.height());
        lp.y = clampY(cy - h / 2, h, b.height());
        view.side = side;
        view.baseAlpha = Prefs.alpha(ctx);
        view.tapToOpen = Prefs.tap(ctx);
        view.invalidate();
    }

    private static int clampY(int y, int h, int screenH) {
        return Math.max(Ui.dp(16), Math.min(screenH - h - Ui.dp(16), y));
    }

    private final class HandleView extends View {
        int side;
        float baseAlpha = 0.6f;
        boolean tapToOpen;
        int accent = Mode.BALANCED.c1;

        private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final int slop;
        private float press;
        private ValueAnimator pressAnim;
        private boolean repositioning, opened, moved;
        private float downX, downY;
        private int downWinY;

        private final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                repositioning = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                animatePress(1f);
            }
        };

        HandleView(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setContentDescription("Abrir el panel de Pulso");
        }

        private void animatePress(float to) {
            if (pressAnim != null) pressAnim.cancel();
            pressAnim = ValueAnimator.ofFloat(press, to);
            pressAnim.setDuration(160);
            pressAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    press = (float) a.getAnimatedValue();
                    invalidate();
                }
            });
            pressAnim.start();
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            // Que el gesto de «atrás» del sistema no se coma el deslizamiento en esta franja.
            setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, r - l, b - t)));
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            post(new Runnable() {
                @Override
                public void run() {
                    relayout();
                }
            });
        }

        @Override
        protected void onDraw(Canvas cv) {
            float a = Math.min(1f, baseAlpha + press * 0.45f);
            if (a <= 0.01f) return;
            float pw = Ui.dpf(4f) + press * Ui.dpf(2f);
            float ph = Math.min(getHeight() * 0.62f, Ui.dpf(72f)) + press * Ui.dpf(8f);
            float cx = side == 0 ? Ui.dpf(5f) + pw / 2f : getWidth() - Ui.dpf(5f) - pw / 2f;
            float cy = getHeight() / 2f;
            r.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f);
            glow.setColor(Ui.alpha(accent, a * 0.5f));
            glow.setShadowLayer(Ui.dpf(6f), 0, 0, Ui.alpha(accent, a * 0.9f));
            cv.drawRoundRect(r, pw / 2f, pw / 2f, glow);
            pill.setColor(Ui.alpha(Ui.mix(0xFFFFFFFF, accent, repositioning ? 0.8f : 0.15f), a));
            cv.drawRoundRect(r, pw / 2f, pw / 2f, pill);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    downWinY = lp.y;
                    opened = moved = repositioning = false;
                    animatePress(0.6f);
                    postDelayed(longPress, 420);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = (e.getRawX() - downX) * (side == 0 ? 1f : -1f);
                    float dy = e.getRawY() - downY;
                    if (repositioning) {
                        lp.y = clampY(downWinY + Math.round(dy), lp.height, bounds().height());
                        push();
                        return true;
                    }
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                        moved = true;
                        removeCallbacks(longPress);
                    }
                    if (!opened && dx > Ui.dpf(16) && dx > Math.abs(dy) * 0.8f) {
                        opened = true;
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        cb.onOpenPanel();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    removeCallbacks(longPress);
                    if (repositioning) {
                        Prefs.put(ctx, Prefs.POS, (lp.y + lp.height / 2f) / Math.max(1, bounds().height()));
                    } else if (!opened && !moved && tapToOpen) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        cb.onOpenPanel();
                    }
                    repositioning = false;
                    animatePress(0f);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPress);
                    repositioning = false;
                    animatePress(0f);
                    return true;
                default:
                    return true;
            }
        }
    }
}
