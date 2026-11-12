package dev.nico.pulso;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * HUD flotante con FPS, temperatura, consumo y batería. No recibe toques: los toques
 * pasan al juego. Por eso su ventana tiene opacidad 0,8: desde Android 12 el sistema
 * bloquea los toques que atraviesan ventanas ajenas más opacas que eso.
 */
final class Hud implements StatsEngine.Listener {
    private final Context ctx;
    private final WindowManager wm;
    private final StatsEngine stats;
    private HudView view;
    private WindowManager.LayoutParams lp;
    private boolean shown;
    private int accent = Mode.BALANCED.c1;

    Hud(Context wctx, StatsEngine stats) {
        ctx = wctx;
        wm = wctx.getSystemService(WindowManager.class);
        this.stats = stats;
    }

    void setVisible(boolean v) {
        if (v) show();
        else hide();
    }

    void setAccent(int c) {
        accent = c;
        if (view != null) view.setAccent(c);
    }

    private void show() {
        if (shown) return;
        view = new HudView(ctx);
        view.setAccent(accent);
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.alpha = 0.8f;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("Pulso:HUD");
        position();
        try {
            wm.addView(view, lp);
            shown = true;
            stats.add(this);
        } catch (RuntimeException e) {
            Log.w("Pulso", "No se pudo mostrar el HUD", e);
        }
    }

    private void hide() {
        if (!shown) return;
        stats.remove(this);
        try {
            wm.removeViewImmediate(view);
        } catch (RuntimeException ignored) {
        }
        shown = false;
        view = null;
    }

    void relayout() {
        if (!shown) return;
        position();
        try {
            wm.updateViewLayout(view, lp);
        } catch (RuntimeException ignored) {
        }
    }

    private void position() {
        WindowMetrics m = wm.getCurrentWindowMetrics();
        Rect b = m.getBounds();
        Insets cut = m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
        Insets bars = m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
        boolean land = b.width() > b.height();
        int pos = Prefs.hudPos(ctx);
        lp.gravity = Gravity.TOP | (pos == 0 ? Gravity.LEFT : (pos == 1 ? Gravity.CENTER_HORIZONTAL : Gravity.RIGHT));
        int sideInset = pos == 0 ? Math.max(cut.left, bars.left) : (pos == 2 ? Math.max(cut.right, bars.right) : 0);
        lp.x = pos == 1 ? 0 : sideInset + Ui.dp(10);
        // En vertical, debajo de la barra de estado; en horizontal (juegos), arriba del todo.
        lp.y = (land ? cut.top : Math.max(cut.top, bars.top)) + Ui.dp(land ? 8 : 4);
    }

    @Override
    public void onStats(Stats s) {
        if (view != null) view.update(s);
    }
}
