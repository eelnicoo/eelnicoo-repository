package dev.nico.pulso;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.RoundedCorner;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * Borde de luz del Modo Gamer: un neón naranja, rosa y morado alrededor de la pantalla, que gira
 * despacio (12 fotogramas por segundo, poco gasto). Son 4 tiras finas en los bordes, no una
 * ventana entera, para no tapar el centro. No reciben toques y tienen opacidad 0,8, para que
 * Android deje pasar los toques al juego.
 */
final class EdgeGlow {
    private static final int[] COLORS = {0xFFFF8A3D, 0xFFFF2F6D, 0xFFB44DFF, 0xFF6D7CFF, 0xFFFF2F6D, 0xFFFF8A3D};

    private final Context ctx;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Strip[] strips = new Strip[4];
    private final WindowManager.LayoutParams[] lps = new WindowManager.LayoutParams[4];
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final Matrix matrix = new Matrix();
    private SweepGradient shader;
    private float radius, angle, cx, cy;
    private boolean shown;
    /** Barras que se pueden tocar (barra de apps de la tablet o botones de navegación). */
    private Insets bars = Insets.NONE;
    private boolean barsKnown;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!shown) return;
            PowerManager pm = ctx.getSystemService(PowerManager.class);
            boolean on = pm == null || pm.isInteractive();
            if (on) {
                angle = (angle + 3f) % 360f;
                matrix.setRotate(angle, cx, cy);
                if (shader != null) shader.setLocalMatrix(matrix);
                for (Strip s : strips) if (s != null) s.invalidate();
            }
            main.postDelayed(this, on ? 80 : 1000);
        }
    };

    EdgeGlow(Context wctx) {
        ctx = wctx;
        wm = wctx.getSystemService(WindowManager.class);
        for (Paint p : new Paint[] {core, glow1, glow2}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        core.setStrokeWidth(Ui.dpf(2.2f));
        glow1.setStrokeWidth(Ui.dpf(5f));
        glow1.setAlpha(110);
        glow2.setStrokeWidth(Ui.dpf(10f));
        glow2.setAlpha(45);
    }

    void setVisible(boolean v) {
        if (v == shown) return;
        if (v) show();
        else hide();
    }

    void relayout() {
        if (!shown) return;
        layout();
        for (int i = 0; i < 4; i++) {
            try {
                wm.updateViewLayout(strips[i], lps[i]);
            } catch (RuntimeException ignored) {
            }
            strips[i].invalidate();
        }
    }

    private void show() {
        for (int i = 0; i < 4; i++) {
            strips[i] = new Strip(ctx);
            if (i == 0) {
                // Cuando aparece o se esconde la barra de apps, se recoloca el borde por encima de ella.
                strips[0].setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                        Insets n = insets.getInsets(WindowInsets.Type.tappableElement());
                        if (!barsKnown || !n.equals(bars)) {
                            bars = n;
                            barsKnown = true;
                            main.post(new Runnable() {
                                @Override
                                public void run() {
                                    relayout();
                                }
                            });
                        }
                        return insets;
                    }
                });
            }
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.alpha = 0.8f;
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setTitle("Pulso:borde");
            lps[i] = lp;
        }
        layout();
        try {
            for (int i = 0; i < 4; i++) wm.addView(strips[i], lps[i]);
            shown = true;
            main.post(tick);
        } catch (RuntimeException e) {
            Log.w("Pulso", "No se pudo mostrar el borde", e);
            hide();
        }
    }

    private void hide() {
        shown = false;
        main.removeCallbacks(tick);
        for (int i = 0; i < 4; i++) {
            if (strips[i] == null) continue;
            try {
                wm.removeViewImmediate(strips[i]);
            } catch (RuntimeException ignored) {
            }
            strips[i] = null;
        }
    }

    private void layout() {
        Rect b = wm.getCurrentWindowMetrics().getBounds();
        WindowInsets wi = wm.getCurrentWindowMetrics().getWindowInsets();
        // Radio real de las esquinas de la pantalla. Si el sistema no lo da (la tablet), esquinas rectas.
        RoundedCorner rc = wi.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        radius = rc != null ? Math.max(0, rc.getRadius()) : 0f;
        // Por encima de la barra de apps o de los botones si se ven (con gestos no hay nada que tocar: 0).
        Insets n = barsKnown ? bars : wi.getInsets(WindowInsets.Type.tappableElement());
        int left = n.left, top = 0, right = b.width() - n.right, bottom = b.height() - n.bottom;
        int w = Math.max(1, right - left), h = Math.max(1, bottom - top);
        int t = Ui.dp(7); // grosor de cada tira (línea + brillo hacia dentro)
        int corner = Math.max(t, Math.round(radius) + t);
        float inset = Ui.dpf(1.6f);
        frame.set(left + inset, top + inset, right - inset, bottom - inset);
        cx = frame.centerX();
        cy = frame.centerY();
        shader = new SweepGradient(cx, cy, COLORS, null);
        matrix.setRotate(angle, cx, cy);
        shader.setLocalMatrix(matrix);
        core.setShader(shader);
        glow1.setShader(shader);
        glow2.setShader(shader);
        // arriba, abajo (con las esquinas), izquierda y derecha (entre ellas)
        set(0, left, top, w, corner);
        set(1, left, bottom - corner, w, corner);
        set(2, left, top + corner, t, Math.max(1, h - 2 * corner));
        set(3, right - t, top + corner, t, Math.max(1, h - 2 * corner));
    }

    private void set(int i, int x, int y, int w, int h) {
        lps[i].x = x;
        lps[i].y = y;
        lps[i].width = w;
        lps[i].height = h;
        strips[i].ox = x;
        strips[i].oy = y;
    }

    private final class Strip extends View {
        int ox, oy;

        Strip(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            cv.translate(-ox, -oy);
            float r = Math.max(0f, radius - Ui.dpf(1.6f));
            cv.drawRoundRect(frame, r, r, glow2);
            cv.drawRoundRect(frame, r, r, glow1);
            cv.drawRoundRect(frame, r, r, core);
        }
    }
}
