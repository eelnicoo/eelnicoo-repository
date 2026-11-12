package dev.nico.pulso;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * El panel de juego. Son dos ventanas superpuestas:
 * una capa de fondo a pantalla completa (oscurece y cierra al tocar fuera) y,
 * encima, el panel de cristal: un Dialog con fondo translúcido y desenfoque real
 * de lo que hay detrás (Window.setBackgroundBlurRadius, Android 12+).
 * Ninguna de las dos coge el foco, para que el juego no se pause ni salgan las barras.
 */
final class GamePanel implements StatsEngine.Listener, Shizuku.Listener {
    private final PulsoService svc;
    private final Context ctx;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private boolean open;
    private View scrim;
    private Dialog dialog;
    private Window window;
    private Consumer<Boolean> blurListener;

    private PanelRoot root;
    private ImageView appIcon;
    private TextView title, subtitle, clock, battery, modeDesc, hint;
    private ModeSelector modes;
    private MetricTile tCpu, tTerm, tGpu, tSkin, tFps, tBat, tPow, tRam;
    private Sparkline spark;
    private ToggleTile gHud, gDnd, gBright, gAwake, gShot, gRam;
    private String pkg;
    private Mode mode = Mode.BALANCED;
    private float maxHz = 60f;

    GamePanel(PulsoService svc, Context wctx) {
        this.svc = svc;
        ctx = wctx;
        wm = wctx.getSystemService(WindowManager.class);
    }

    boolean isOpen() {
        return open;
    }

    // ---------------------------------------------------------------- abrir / cerrar

    void open() {
        if (open) return;
        open = true;
        pkg = null;
        mode = Prefs.mode(ctx);
        maxHz = Sys.maxRefresh(ctx);

        WindowMetrics metrics = wm.getCurrentWindowMetrics();
        Rect b = metrics.getBounds();
        WindowInsets wi = metrics.getWindowInsets();
        Insets cut = wi.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
        Insets bars = wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
        int W = b.width(), H = b.height();
        boolean land = W > H;
        int side = Prefs.side(ctx);

        int left = Math.max(cut.left, bars.left) + Ui.dp(12);
        int right = W - Math.max(cut.right, bars.right) - Ui.dp(12);
        int top, bottom;
        if (land) { // en horizontal (juegos) las barras suelen estar ocultas: solo se evita el recorte de la cámara
            top = cut.top + Ui.dp(10);
            bottom = H - cut.bottom - Ui.dp(10);
        } else {
            top = Math.max(cut.top, bars.top) + Ui.dp(8);
            bottom = H - Math.max(cut.bottom, bars.bottom) - Ui.dp(8);
        }
        boolean tablet = Math.min(W, H) >= Ui.dp(600);
        int panelW = land ? Math.min(Math.round((right - left) * 0.74f), Ui.dp(tablet ? 780 : 660))
                : Math.min(right - left, Ui.dp(tablet ? 540 : 392));
        int maxH = Math.max(Ui.dp(200), bottom - top);

        build(land, side == 1);
        root.measure(View.MeasureSpec.makeMeasureSpec(panelW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
        int panelH = root.getMeasuredHeight();
        int x = side == 0 ? left : right - panelW;
        int y = top + (maxH - panelH) / 2;

        addScrim(W, H);
        try {
            showDialog(panelW, panelH, x, y, side);
        } catch (RuntimeException e) {
            Log.w("Pulso", "No se pudo abrir el panel", e);
            dialog = null;
            onDismissed();
            return;
        }
        refreshToggles();
        svc.stats().add(this);
        Shizuku.addListener(this);
        detectApp();
    }

    void close() {
        if (!open) return;
        if (dialog != null) {
            dialog.dismiss(); // → onDismissed()
        } else {
            onDismissed();
        }
    }

    /** Cierre inmediato, sin animación (el servicio se para). */
    void destroy() {
        if (!open) return;
        if (scrim != null) {
            try {
                wm.removeViewImmediate(scrim);
            } catch (RuntimeException ignored) {
            }
            scrim = null;
        }
        close();
    }

    private void onDismissed() {
        if (!open) return;
        open = false;
        svc.stats().remove(this);
        Shizuku.removeListener(this);
        if (blurListener != null) {
            try {
                wm.removeCrossWindowBlurEnabledListener(blurListener);
            } catch (RuntimeException ignored) {
            }
            blurListener = null;
        }
        dialog = null;
        window = null;
        final View s = scrim;
        scrim = null;
        if (s != null) {
            s.animate().alpha(0f).setDuration(170).withEndAction(new Runnable() {
                @Override
                public void run() {
                    try {
                        wm.removeViewImmediate(s);
                    } catch (RuntimeException ignored) {
                    }
                }
            }).start();
        }
    }

    private void addScrim(int w, int h) {
        View v = new View(ctx) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) close();
                return true;
            }
        };
        v.setBackgroundColor(0x66000000);
        v.setAlpha(0f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("Pulso:fondo");
        try {
            wm.addView(v, lp);
            scrim = v;
            v.animate().alpha(1f).setDuration(220).start();
        } catch (RuntimeException e) {
            Log.w("Pulso", "No se pudo añadir el fondo", e);
        }
    }

    private void showDialog(int w, int h, int x, int y, int side) {
        dialog = new Dialog(ctx, R.style.PulsoPanel);
        window = dialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(root, new ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH));
        window.setLayout(w, h);
        window.setGravity(Gravity.TOP | Gravity.LEFT);
        window.setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams a = window.getAttributes();
        a.x = x;
        a.y = y;
        a.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        a.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        a.windowAnimations = side == 0 ? R.style.PanelAnimLeft : R.style.PanelAnimRight;
        a.setTitle("Pulso:panel");
        window.setAttributes(a);
        applyGlass(wm.isCrossWindowBlurEnabled());
        blurListener = new Consumer<Boolean>() {
            @Override
            public void accept(Boolean enabled) {
                applyGlass(enabled != null && enabled);
            }
        };
        wm.addCrossWindowBlurEnabledListener(blurListener);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                onDismissed();
            }
        });
        dialog.show();
    }

    /** Cristal: translúcido y desenfocado; si el sistema no permite desenfoques, casi opaco. */
    private void applyGlass(boolean blur) {
        if (window == null) return;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(Ui.dpf(PanelRoot.RADIUS_DP));
        g.setColor(blur ? 0x9E10131A : 0xF20D1016);
        g.setStroke(Math.max(1, Ui.dp(1)), 0x26FFFFFF);
        window.setBackgroundDrawable(g);
        window.setBackgroundBlurRadius(blur ? Ui.dp(40) : 0);
    }

    // ---------------------------------------------------------------- contenido

    private void build(boolean land, boolean mirror) {
        Context c = ctx;
        root = new PanelRoot(c);
        root.setMirror(mirror);
        root.setAccent(mode.c1, mode.c2, false);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = Ui.column(c);
        int pad = Ui.dp(16);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(scroll, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        int gap = Ui.dp(10);
        col.addView(header(c), Ui.lp(Ui.MATCH, Ui.WRAP));

        modes = new ModeSelector(c, true);
        modes.setMode(mode, false);
        modes.setListener(new ModeSelector.Listener() {
            @Override
            public void onModeSelected(Mode m) {
                ModeController.select(ctx, m, pkg, false);
                // El servicio verá el cambio en los ajustes y llamará a onModeChanged().
            }
        });
        modeDesc = Ui.textDp(c, mode.describe(Shizuku.ready(), maxHz), 11.5f, Ui.TEXT2, Ui.MEDIUM);
        modeDesc.setLineSpacing(Ui.dpf(2), 1f);

        int tileH = land ? 76 : 82;
        tCpu = new MetricTile(c, "CPU", tileH);
        tTerm = new MetricTile(c, "TÉRMICO", tileH);
        tGpu = new MetricTile(c, "GPU", tileH);
        tSkin = new MetricTile(c, "CARCASA", tileH);
        tFps = new MetricTile(c, "FPS", tileH);
        tBat = new MetricTile(c, "BATERÍA", tileH);
        tPow = new MetricTile(c, "CONSUMO", tileH);
        tRam = new MetricTile(c, "RAM", tileH);
        spark = new Sparkline(c, land ? 74 : 84);

        View grid = grid(c, new View[] {tCpu, tTerm, tGpu, tSkin, tFps, tBat, tPow, tRam}, 4);
        tFps.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                copyDiagnostic();
                return true;
            }
        });
        View toggles = toggles(c, land ? 62 : 66);

        if (land) {
            LinearLayout cols = Ui.row(c);
            cols.setGravity(Gravity.TOP);
            LinearLayout a = Ui.column(c);
            a.addView(grid, Ui.lp(Ui.MATCH, Ui.WRAP));
            a.addView(spark, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(8), 0, 0));
            LinearLayout bcol = Ui.column(c);
            bcol.addView(modes, Ui.lp(Ui.MATCH, Ui.WRAP));
            bcol.addView(modeDesc, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(4), Ui.dp(8), Ui.dp(4), 0));
            bcol.addView(toggles, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(10), 0, 0));
            cols.addView(a, Ui.lp(0, Ui.WRAP, 1.6f));
            LinearLayout.LayoutParams bp = Ui.lp(0, Ui.WRAP, 1f);
            bp.leftMargin = Ui.dp(12);
            cols.addView(bcol, bp);
            col.addView(cols, Ui.lp(Ui.MATCH, Ui.WRAP, 0, gap + Ui.dp(2), 0, 0));
        } else {
            col.addView(modes, Ui.lp(Ui.MATCH, Ui.WRAP, 0, gap + Ui.dp(4), 0, 0));
            col.addView(modeDesc, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(4), Ui.dp(8), Ui.dp(4), 0));
            col.addView(grid, Ui.lp(Ui.MATCH, Ui.WRAP, 0, gap + Ui.dp(4), 0, 0));
            col.addView(spark, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(8), 0, 0));
            col.addView(toggles, Ui.lp(Ui.MATCH, Ui.WRAP, 0, gap, 0, 0));
        }

        hint = Ui.textDp(c, "Conecta Shizuku para limitar a 60 FPS exactos y ver CPU, GPU y FPS reales  ›",
                11f, Ui.TEXT2, Ui.MEDIUM);
        hint.setBackground(Ui.ripple(Ui.rect(Ui.alpha(Ui.WARN, 0.10f), 12, Ui.alpha(Ui.WARN, 0.28f)), 12, 0x22FFFFFF));
        hint.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
        hint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openApp();
            }
        });
        hint.setVisibility(Shizuku.ready() ? View.GONE : View.VISIBLE);
        col.addView(hint, Ui.lp(Ui.MATCH, Ui.WRAP, 0, gap, 0, 0));

        applyAccent(false);
        Stats last = svc.stats().last();
        if (last != null) onStats(last);
    }

    private View header(Context c) {
        LinearLayout row = Ui.row(c);
        appIcon = new ImageView(c);
        appIcon.setImageDrawable(badge(c));
        appIcon.setClipToOutline(true);
        appIcon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dpf(11));
            }
        });
        row.addView(appIcon, Ui.lp(Ui.dp(36), Ui.dp(36)));

        LinearLayout texts = Ui.column(c);
        title = Ui.textDp(c, "Pulso", 15.5f, Ui.TEXT, Ui.SEMIBOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle = Ui.textDp(c, "", 11.5f, Ui.TEXT2, Ui.MEDIUM);
        subtitle.setSingleLine(true);
        texts.addView(title);
        texts.addView(subtitle, Ui.lp(Ui.WRAP, Ui.WRAP, 0, Ui.dp(4), 0, 0));
        LinearLayout.LayoutParams tp = Ui.lp(0, Ui.WRAP, 1f);
        tp.leftMargin = Ui.dp(11);
        row.addView(texts, tp);

        LinearLayout right = Ui.column(c);
        right.setGravity(Gravity.END);
        clock = Ui.textDp(c, clockFmt.format(new Date()), 13.5f, Ui.TEXT, Ui.SEMIBOLD);
        clock.setFontFeatureSettings("tnum");
        clock.setSingleLine(true);
        clock.setMinWidth(Math.round(clock.getPaint().measureText("00:00")) + Ui.dp(2));
        battery = Ui.textDp(c, "", 11.5f, Ui.TEXT2, Ui.MEDIUM);
        battery.setFontFeatureSettings("tnum");
        battery.setSingleLine(true);
        right.addView(clock, Ui.lp(Ui.WRAP, Ui.WRAP));
        right.addView(battery, Ui.lp(Ui.WRAP, Ui.WRAP, 0, Ui.dp(4), 0, 0));
        row.addView(right, Ui.lp(Ui.WRAP, Ui.WRAP, Ui.dp(8), 0, 0, 0));

        ImageView settings = new ImageView(c);
        settings.setImageDrawable(Ui.icon(c, R.drawable.ic_tune, Ui.TEXT2));
        settings.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        settings.setPadding(Ui.dp(9), Ui.dp(9), Ui.dp(9), Ui.dp(9));
        settings.setBackground(Ui.ripple(Ui.rect(Ui.TILE, 12, Ui.TILE_STROKE), 12, 0x22FFFFFF));
        settings.setContentDescription("Ajustes de Pulso");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openApp();
            }
        });
        row.addView(settings, Ui.lp(Ui.dp(36), Ui.dp(36), Ui.dp(12), 0, 0, 0));
        return row;
    }

    /** Logo de Pulso: cuadrado con el degradado del modo y la línea de pulso. */
    private Drawable badge(Context c) {
        GradientDrawable bg = Ui.gradient(mode.c1, mode.c2, 11);
        LayerDrawable l = new LayerDrawable(new Drawable[] {bg, Ui.icon(c, R.drawable.ic_pulse, Ui.INK)});
        int in = Ui.dp(8);
        l.setLayerInset(1, in, in, in, in);
        return l;
    }

    private View grid(Context c, View[] cells, int cols) {
        LinearLayout grid = Ui.column(c);
        int g = Ui.dp(8);
        for (int i = 0; i < cells.length; i += cols) {
            LinearLayout row = Ui.row(c);
            for (int k = 0; k < cols && i + k < cells.length; k++) {
                LinearLayout.LayoutParams p = Ui.lp(0, Ui.WRAP, 1f);
                if (k > 0) p.leftMargin = g;
                row.addView(cells[i + k], p);
            }
            grid.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, 0, i == 0 ? 0 : g, 0, 0));
        }
        return grid;
    }

    private View toggles(final Context c, int h) {
        gHud = new ToggleTile(c, R.drawable.ic_gauge, "HUD", h);
        gDnd = new ToggleTile(c, R.drawable.ic_moon, "No molestar", h);
        gBright = new ToggleTile(c, R.drawable.ic_sun, "Brillo fijo", h);
        gAwake = new ToggleTile(c, R.drawable.ic_cup, "No apagar", h);
        gShot = new ToggleTile(c, R.drawable.ic_capture, "Captura", h);
        gRam = new ToggleTile(c, R.drawable.ic_sparkle, "Liberar RAM", h);

        gHud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.hud(c);
                Prefs.put(c, Prefs.HUD, on);
                gHud.setActive(on);
            }
        });
        gAwake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.awake(c);
                Prefs.put(c, Prefs.AWAKE, on);
                gAwake.setActive(on);
            }
        });
        gDnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !gDnd.isActive();
                if (Sys.setDnd(c, on)) {
                    gDnd.setActive(on);
                    main.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (open) gDnd.setActive(Sys.dndOn(c));
                        }
                    }, 700);
                } else {
                    close();
                    Sys.open(c, new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    Sys.toast(c, "Permite «No molestar» a Pulso");
                }
            }
        });
        gBright.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean lock = !gBright.isActive();
                if (Sys.setAutoBrightness(c, !lock)) {
                    gBright.setActive(lock);
                } else {
                    close();
                    Sys.open(c, Sys.settings(c, Settings.ACTION_MANAGE_WRITE_SETTINGS, true));
                    Sys.toast(c, "Permite a Pulso modificar los ajustes del sistema");
                }
            }
        });
        gShot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Shizuku.ready()) {
                    Sys.toast(c, "La captura desde el panel necesita Shizuku");
                    return;
                }
                close();
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Shell.async("input keyevent 120"); // KEYCODE_SYSRQ: la captura del sistema
                    }
                }, 450);
            }
        });
        gRam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Shizuku.ready()) {
                    Sys.toast(c, "Liberar RAM necesita Shizuku");
                    return;
                }
                gRam.setActive(true);
                gRam.flash("Liberando…");
                Sys.freeRam(c, new Sys.LongCallback() {
                    @Override
                    public void done(long freed) {
                        if (!open) return;
                        gRam.setActive(false);
                        long mb = freed / (1024 * 1024);
                        gRam.flash(mb > 0 ? "+" + mb + " MB" : "Hecho");
                    }
                });
            }
        });
        return grid(c, new View[] {gHud, gDnd, gBright, gAwake, gShot, gRam}, 3);
    }

    private void refreshToggles() {
        if (!open || gHud == null) return;
        gHud.setActive(Prefs.hud(ctx));
        gAwake.setActive(Prefs.awake(ctx));
        gDnd.setActive(Sys.dndOn(ctx));
        gBright.setActive(!Sys.autoBrightness(ctx));
        boolean sz = Shizuku.ready();
        gShot.setAvailable(sz);
        gRam.setAvailable(sz);
        hint.setVisibility(sz ? View.GONE : View.VISIBLE);
        modeDesc.setText(mode.describe(sz, maxHz));
    }

    private void applyAccent(boolean animate) {
        root.setAccent(mode.c1, mode.c2, animate);
        ToggleTile[] all = {gHud, gDnd, gBright, gAwake, gShot, gRam};
        for (ToggleTile t : all) t.setAccent(mode.c1, mode.c2);
        if (pkg == null) appIcon.setImageDrawable(badge(ctx));
    }

    private void detectApp() {
        Sys.BG.execute(new Runnable() {
            @Override
            public void run() {
                final String p = Sys.foregroundPackage(ctx);
                if (p == null) return;
                final CharSequence label = Sys.appLabel(ctx, p);
                final Drawable icon = Sys.appIcon(ctx, p);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!open) return;
                        pkg = p;
                        if (label != null) title.setText(label);
                        if (icon != null) appIcon.setImageDrawable(icon);
                    }
                });
            }
        });
    }

    /** Copia al portapapeles lo que devuelve SurfaceFlinger para el juego abierto, para depurar los FPS. */
    private void copyDiagnostic() {
        final String p = pkg;
        if (!Shizuku.ready() || p == null || !Sys.PACKAGE_NAME.matcher(p).matches()) {
            Sys.toast(ctx, "Abre el panel dentro de un juego, con Shizuku conectado");
            return;
        }
        Sys.toast(ctx, "Preparando el diagnóstico…");
        Sys.BG.execute(new Runnable() {
            @Override
            public void run() {
                String list = Shell.run("dumpsys SurfaceFlinger --list | grep -F '" + p + "'", 5000);
                String layer = list != null ? StatsEngine.pickLayer(list, p) : null;
                String out = Shell.run("echo \"Pulso 1.0.2 · $(getprop ro.product.model) · Android $(getprop ro.build.version.release)\"; "
                        + "echo '== capas'; dumpsys SurfaceFlinger --list | grep -F '" + p + "'; "
                        + "echo '== latency'; " + (layer != null ? "dumpsys SurfaceFlinger --latency '" + layer + "' | head -n 6" : "echo sin capa") + "; "
                        + "echo '== timestats'; dumpsys SurfaceFlinger --timestats -enable >/dev/null; sleep 1; "
                        + "dumpsys SurfaceFlinger --timestats -dump | grep -B2 -A14 -F '" + p + "' | head -n 60", 12000);
                final String text = out != null ? out : "Shizuku no respondió";
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        android.content.ClipboardManager cm = ctx.getSystemService(android.content.ClipboardManager.class);
                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico de Pulso", text));
                        Sys.toast(ctx, "Diagnóstico copiado: pégaselo a Claude");
                    }
                });
            }
        });
    }

    private void openApp() {
        close();
        Sys.open(ctx, new Intent(ctx, MainActivity.class));
    }

    // ---------------------------------------------------------------- cambios

    void onModeChanged(Mode m) {
        if (!open || m == mode) return;
        mode = m;
        if (modes.mode() != m) modes.setMode(m, true);
        modeDesc.setText(m.describe(Shizuku.ready(), maxHz));
        applyAccent(true);
        Stats last = svc.stats().last();
        if (last != null) onStats(last);
    }

    @Override
    public void onShizukuChanged() {
        refreshToggles();
    }

    @Override
    public void onStats(Stats s) {
        if (!open || tCpu == null) return;
        clock.setText(clockFmt.format(new Date()));
        battery.setText(s.battery >= 0 ? s.battery + " %" + (s.charging ? " · cargando" : "") : "");
        subtitle.setText(mode.label + " · " + Ui.f0(s.refresh) + " Hz");

        String needs = s.shizuku ? "Sin sensor" : "Con Shizuku";

        // CPU: uso y frecuencia.
        if (Stats.ok(s.cpuLoad)) {
            tCpu.set("CPU", Ui.f0(s.cpuLoad * 100f), "%", Stats.ok(s.cpuGhz) ? Ui.f1(s.cpuGhz) + " GHz" : "Uso",
                    s.cpuLoad, s.cpuLoad > 0.85f ? Ui.WARN : mode.c1);
        } else {
            tCpu.set("CPU", "—", "", s.shizuku ? "Midiendo…" : "Con Shizuku", 0f, Ui.TEXT3);
        }

        // Térmico: temperatura de la CPU y el estado térmico del sistema.
        if (Stats.ok(s.cpuTemp)) {
            tTerm.set("TÉRMICO", Ui.f0(s.cpuTemp), "°C", Sys.thermalName(s.thermal),
                    (s.cpuTemp - 25f) / 70f, Ui.severity(s.cpuTemp, 60f, 80f));
        } else {
            float frac = Stats.ok(s.headroom) ? s.headroom : s.thermal / 6f;
            tTerm.set("TÉRMICO", Sys.thermalName(s.thermal), "", needs, frac, Sys.thermalColor(s.thermal));
        }

        if (Stats.ok(s.gpuTemp)) {
            tGpu.set("GPU", Ui.f0(s.gpuTemp), "°C", "Gráficos", (s.gpuTemp - 25f) / 70f, Ui.severity(s.gpuTemp, 60f, 80f));
        } else {
            tGpu.set("GPU", "—", "", needs, 0f, Ui.TEXT3);
        }

        if (Stats.ok(s.skinTemp)) {
            tSkin.set("CARCASA", Ui.f0(s.skinTemp), "°C", "Exterior", (s.skinTemp - 20f) / 30f, Ui.severity(s.skinTemp, 40f, 45f));
        } else {
            tSkin.set("CARCASA", "—", "", needs, 0f, Ui.TEXT3);
        }

        float max = s.maxRefresh > 0 ? s.maxRefresh : 60f;
        if (Stats.ok(s.fps)) {
            tFps.set("FPS", Ui.f0(s.fps), "", Ui.f0(s.refresh) + " Hz", s.fps / max, mode.c1);
        } else {
            tFps.set("PANTALLA", Ui.f0(s.refresh), "Hz", "máx. " + Ui.f0(max), s.refresh / max, mode.c1);
        }

        tBat.set("BATERÍA", Stats.ok(s.batteryTemp) ? Ui.f1(s.batteryTemp) : "—", "°C",
                (s.battery >= 0 ? s.battery + " %" : "") + (s.charging ? " · carga" : ""),
                s.battery / 100f, Ui.severity(s.batteryTemp, 38f, 43f));

        if (Stats.ok(s.power)) {
            tPow.set(s.charging ? "CARGA" : "CONSUMO", Ui.f1(s.power), "W", Ui.f2(s.current) + " A", s.power / 12f,
                    s.charging ? Ui.GOOD : Ui.severity(s.power, 5f, 9f));
        } else {
            tPow.set("CONSUMO", "—", "", "Sin datos", 0f, Ui.TEXT3);
        }

        if (s.ramTotal > 0) {
            float used = s.ramUsed / 1073741824f;
            long total = Math.round(Math.ceil(s.ramTotal / 1e9));
            tRam.set("RAM", Ui.f1(used), "GB", "de " + total + " GB", s.ramUsed / (float) s.ramTotal, mode.c2);
        }

        boolean fps = Stats.any(s.histFps);
        boolean cpu = Stats.any(s.histCpu);
        spark.set(fps ? s.histFps : s.histPow, fps ? "FPS" : "W",
                fps ? Ui.f0(s.fps) : (Stats.ok(s.power) ? Ui.f1(s.power) : "—"),
                fps ? Math.max(60f, max) : 12f, mode.c1,
                cpu ? s.histCpu : s.histBat, cpu ? "CPU" : "BAT",
                (cpu ? (Stats.ok(s.cpuTemp) ? Ui.f0(s.cpuTemp) : "—") : (Stats.ok(s.batteryTemp) ? Ui.f0(s.batteryTemp) : "—")) + "°",
                cpu ? 25f : 20f, cpu ? 95f : 50f);
    }
}
