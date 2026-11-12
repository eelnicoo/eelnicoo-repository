package dev.nico.pulso;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Pantalla de la app: activar el panel, elegir modo, permisos, Shizuku y ajustes del tirador y el HUD. */
public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener, Shizuku.Listener {
    private static final int REQ_NOTIF = 11;
    private static final String SHIZUKU_URL = "https://shizuku.rikka.app/download/";

    private boolean pendingStart;
    private AmbientView ambient;
    private LinearLayout content;

    private TextView statusChip, heroTitle, heroText, mainButton, openButton, modeDesc;
    private ModeSelector modes;
    private TextView szStatus, szButton, szSteps;
    private View szDot;
    private PermRow pOverlay, pNotif, pUsage, pWrite;
    private Segmented sideSeg, hudSeg, ecoSeg, balSeg;
    private Slider posSlider, sizeSlider, alphaSlider;
    private TextView posVal, sizeVal, alphaVal;
    private ToggleSwitch tapSw, hudSw, awakeSw, bootSw, edgeSw;

    // ---------------------------------------------------------------- ciclo de vida

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setNavigationBarContrastEnforced(false);

        FrameLayout root = new FrameLayout(this);
        ambient = new AmbientView(this);
        root.addView(ambient, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        final ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        content = Ui.column(this);
        scroll.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(scroll, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int side = Ui.dp(18);
                content.setPadding(side + i.left, i.top + Ui.dp(18), side + i.right, i.bottom + Ui.dp(28));
                return WindowInsets.CONSUMED;
            }
        });

        buildHero();
        buildMode();
        buildShizuku();
        buildPermissions();
        buildHandle();
        buildHud();
        buildGeneral();
        buildDiag();
        TextView foot = Ui.text(this, "Pulso 1.0 · sin internet · los modos nunca activan el ahorro de batería",
                12f, Ui.TEXT3, Ui.MEDIUM);
        foot.setGravity(Gravity.CENTER);
        content.addView(foot, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(8), 0, 0));

        setContentView(root);
        Mode m = Prefs.mode(this);
        ambient.setAccent(m.c1, m.c2, false);
        applyAccent(m);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        Shizuku.addListener(this);
        Shizuku.refresh();
        if (pendingStart && Settings.canDrawOverlays(this)) {
            pendingStart = false;
            startPulso();
        }
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        Shizuku.removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        refresh();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.POS.equals(key) || Prefs.SIZE.equals(key) || Prefs.ALPHA.equals(key)) {
            updateSliderLabels(); // llega muchas veces por segundo mientras se arrastra
            return;
        }
        if (Prefs.MODE.equals(key)) {
            Mode m = Prefs.mode(this);
            if (modes.mode() != m) modes.setMode(m, true);
            ambient.setAccent(m.c1, m.c2, true);
            applyAccent(m);
        }
        refresh();
    }

    @Override
    public void onShizukuChanged() {
        refresh();
    }

    // ---------------------------------------------------------------- acciones

    private void startPulso() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true;
            Sys.toast(this, "Activa «Mostrar sobre otras apps» para Pulso");
            startActivity(Sys.settings(this, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true));
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        PulsoService.start(this);
        Sys.MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 400);
    }

    private void stopPulso() {
        PulsoService.stop(this);
        Sys.MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 400);
    }

    private void shizukuAction() {
        switch (Shizuku.state(this)) {
            case NOT_INSTALLED:
                Sys.open(this, new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_URL)));
                break;
            case NOT_RUNNING: {
                Intent i = getPackageManager().getLaunchIntentForPackage(Shizuku.PACKAGE);
                if (i != null) Sys.open(this, i);
                break;
            }
            case NO_PERMISSION:
                Shizuku.requestPermission();
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- refresco

    private void refresh() {
        boolean on = PulsoService.running;
        boolean overlay = Settings.canDrawOverlays(this);
        String edge = Prefs.side(this) == 0 ? "izquierdo" : "derecho";

        statusChip.setText(on ? "●  Activo" : "●  Apagado");
        statusChip.setTextColor(on ? Ui.GOOD : Ui.TEXT3);
        statusChip.setBackground(Ui.rect(on ? Ui.alpha(Ui.GOOD, 0.12f) : Ui.TILE, 20, on ? Ui.alpha(Ui.GOOD, 0.35f) : Ui.TILE_STROKE));

        if (on) {
            heroTitle.setText("Listo para jugar");
            heroText.setText("Desliza desde el borde " + edge + " de la pantalla para abrir el panel, encima de cualquier juego o app.");
        } else if (!overlay) {
            heroTitle.setText("Activa el panel");
            heroText.setText("Primero, permite a Pulso mostrarse sobre otras apps. Después aparecerá un tirador fino en el borde de la pantalla.");
        } else {
            heroTitle.setText("Activa el panel");
            heroText.setText("Aparecerá un tirador fino en el borde " + edge + ". Desliza desde él hacia dentro para abrir el panel.");
        }
        Mode m = Prefs.mode(this);
        styleButton(mainButton, !on, m);
        mainButton.setText(on ? "Desactivar" : "Activar");
        openButton.setVisibility(on ? View.VISIBLE : View.GONE);

        modeDesc.setText(m.describe(Shizuku.ready(), Sys.maxRefresh(this), Prefs.ecoFps(this), Prefs.balFps(this)));
        if (ecoSeg != null) ecoSeg.select(Prefs.ecoFps(this) == 60 ? 1 : 0, false);
        if (balSeg != null) balSeg.select(Prefs.balFps(this) == 60 ? 0 : 1, false);
        edgeSw.setChecked(Prefs.edge(this), false);

        Shizuku.State st = Shizuku.state(this);
        int dotColor;
        switch (st) {
            case READY:
                szStatus.setText("Conectado. Funciones avanzadas activas.");
                szButton.setText("Listo");
                dotColor = Ui.GOOD;
                break;
            case NO_PERMISSION:
                szStatus.setText("Shizuku funciona, falta dar permiso a Pulso.");
                szButton.setText("Dar permiso");
                dotColor = Ui.WARN;
                break;
            case NOT_RUNNING:
                szStatus.setText("Instalado, pero no está iniciado.");
                szButton.setText("Abrir Shizuku");
                dotColor = Ui.WARN;
                break;
            default:
                szStatus.setText("No está instalado.");
                szButton.setText("Descargar");
                dotColor = Ui.TEXT3;
                break;
        }
        ((GradientDrawable) szDot.getBackground()).setColor(dotColor);
        boolean ready = st == Shizuku.State.READY;
        szButton.setEnabled(!ready);
        szButton.setTextColor(ready ? Ui.GOOD : Ui.TEXT);
        szButton.setBackground(ready ? Ui.rect(Ui.alpha(Ui.GOOD, 0.12f), 14, Ui.alpha(Ui.GOOD, 0.35f))
                : Ui.ripple(Ui.rect(0x1AFFFFFF, 14, 0x24FFFFFF), 14, 0x33FFFFFF));
        szSteps.setVisibility(ready ? View.GONE : View.VISIBLE);

        pOverlay.set(overlay);
        pNotif.set(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        pUsage.set(Sys.usageAccess(this));
        pWrite.set(Settings.System.canWrite(this) || ready);

        sideSeg.select(Prefs.side(this), false);
        hudSeg.select(Prefs.hudPos(this), false);
        posSlider.setValue((Prefs.pos(this) - 0.1f) / 0.8f);
        sizeSlider.setValue((Prefs.size(this) - 70f) / 150f);
        alphaSlider.setValue(Prefs.alpha(this));
        updateSliderLabels();
        diagText.setText(Diag.summary(this));
        tapSw.setChecked(Prefs.tap(this), false);
        hudSw.setChecked(Prefs.hud(this), false);
        awakeSw.setChecked(Prefs.awake(this), false);
        bootSw.setChecked(Prefs.boot(this), false);
    }

    private void updateSliderLabels() {
        float pos = Prefs.pos(this);
        posVal.setText(pos < 0.36f ? "Arriba" : (pos < 0.64f ? "Centro" : "Abajo"));
        sizeVal.setText(Prefs.size(this) + " dp");
        float a = Prefs.alpha(this);
        alphaVal.setText(a < 0.03f ? "Invisible" : Ui.f0(a * 100f) + " %");
    }

    private void applyAccent(Mode m) {
        Slider[] sliders = {posSlider, sizeSlider, alphaSlider};
        for (Slider s : sliders) s.setAccent(m.c1, m.c2);
        ToggleSwitch[] sw = {tapSw, hudSw, awakeSw, bootSw, edgeSw};
        for (ToggleSwitch s : sw) s.setAccent(m.c1, m.c2);
        styleButton(mainButton, !PulsoService.running, m);
    }

    private void styleButton(TextView b, boolean primary, Mode m) {
        if (primary) {
            b.setBackground(Ui.ripple(Ui.gradient(m.c1, m.c2, 18), 18, 0x33FFFFFF));
            b.setTextColor(Ui.INK);
        } else {
            b.setBackground(Ui.ripple(Ui.rect(0x14FFFFFF, 18, 0x22FFFFFF), 18, 0x33FFFFFF));
            b.setTextColor(Ui.TEXT);
        }
    }

    // ---------------------------------------------------------------- secciones

    private void buildHero() {
        LinearLayout top = Ui.row(this);
        ImageView logo = new ImageView(this);
        Mode m = Prefs.mode(this);
        LayerDrawable badge = new LayerDrawable(new Drawable[] {
                Ui.gradient(m.c1, m.c2, 15), Ui.icon(this, R.drawable.ic_pulse, Ui.INK)});
        badge.setLayerInset(1, Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        logo.setImageDrawable(badge);
        top.addView(logo, Ui.lp(Ui.dp(48), Ui.dp(48)));

        LinearLayout names = Ui.column(this);
        names.addView(Ui.text(this, "Pulso", 28f, Ui.TEXT, Ui.BOLD));
        names.addView(Ui.text(this, "Panel de juego", 14f, Ui.TEXT2, Ui.MEDIUM));
        LinearLayout.LayoutParams np = Ui.lp(0, Ui.WRAP, 1f);
        np.leftMargin = Ui.dp(14);
        top.addView(names, np);

        statusChip = Ui.text(this, "", 12.5f, Ui.TEXT3, Ui.SEMIBOLD);
        statusChip.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        top.addView(statusChip, Ui.lp(Ui.WRAP, Ui.WRAP));
        content.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(6), 0, Ui.dp(20)));

        LinearLayout card = card(null);
        heroTitle = Ui.text(this, "", 21f, Ui.TEXT, Ui.SEMIBOLD);
        heroText = Ui.text(this, "", 14.5f, Ui.TEXT2, Ui.REGULAR);
        heroText.setLineSpacing(Ui.dpf(3), 1f);
        card.addView(heroTitle);
        card.addView(heroText, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(6), 0, Ui.dp(16)));

        mainButton = Ui.text(this, "Activar", 16f, Ui.INK, Ui.SEMIBOLD);
        mainButton.setGravity(Gravity.CENTER);
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PulsoService.running) stopPulso();
                else startPulso();
            }
        });
        card.addView(mainButton, Ui.lp(Ui.MATCH, Ui.dp(54)));

        openButton = Ui.text(this, "Abrir el panel ahora", 14.5f, Ui.TEXT, Ui.MEDIUM);
        openButton.setGravity(Gravity.CENTER);
        openButton.setBackground(Ui.ripple(Ui.rect(0x00000000, 16), 16, 0x22FFFFFF));
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, PulsoService.class).setAction(PulsoService.ACTION_OPEN));
            }
        });
        card.addView(openButton, Ui.lp(Ui.MATCH, Ui.dp(46), 0, Ui.dp(6), 0, 0));
        content.addView(card, cardLp());
    }

    private void buildMode() {
        LinearLayout card = card("Modo");
        modes = new ModeSelector(this, false, Mode.available(Sys.maxRefresh(this)));
        modes.setMode(Prefs.mode(this), false);
        modes.setListener(new ModeSelector.Listener() {
            @Override
            public void onModeSelected(Mode m) {
                ModeController.select(MainActivity.this, m, null, false);
            }
        });
        card.addView(modes, Ui.lp(Ui.MATCH, Ui.WRAP));
        modeDesc = Ui.text(this, "", 13.5f, Ui.TEXT2, Ui.REGULAR);
        modeDesc.setLineSpacing(Ui.dpf(2), 1f);
        card.addView(modeDesc, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(2), Ui.dp(12), Ui.dp(2), 0));
        if (Sys.maxRefresh(this) >= 61f) {
            card.addView(label("Límite en Equilibrado"), Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(16), 0, 0));
            balSeg = new Segmented(this, "60 FPS", Ui.f0(Sys.maxRefresh(this)) + " FPS");
            balSeg.setListener(new Segmented.Listener() {
                @Override
                public void onSelect(Segmented s, int index) {
                    Prefs.put(MainActivity.this, Prefs.BAL_FPS, index == 0 ? 60 : 120);
                    if (!PulsoService.running && Prefs.mode(MainActivity.this) == Mode.BALANCED) {
                        ModeController.apply(MainActivity.this, Mode.BALANCED);
                    }
                }
            });
            card.addView(balSeg, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(8), 0, 0));
            card.addView(label("Límite en Eco"), Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(16), 0, 0));
            ecoSeg = new Segmented(this, "30 FPS", "60 FPS");
            ecoSeg.setListener(new Segmented.Listener() {
                @Override
                public void onSelect(Segmented s, int index) {
                    Prefs.put(MainActivity.this, Prefs.ECO_FPS, index == 1 ? 60 : 30);
                    // Si el panel no está activo, el servicio no lo verá: se aplica aquí.
                    if (!PulsoService.running && Prefs.mode(MainActivity.this) == Mode.ECO) {
                        ModeController.apply(MainActivity.this, Mode.ECO);
                    }
                }
            });
            card.addView(ecoSeg, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(8), 0, 0));
        }
        content.addView(card, cardLp());
    }

    private void buildShizuku() {
        LinearLayout card = card("Funciones avanzadas");
        LinearLayout row = Ui.row(this);
        row.addView(iconBadge(R.drawable.ic_terminal), Ui.lp(Ui.dp(40), Ui.dp(40)));
        LinearLayout texts = Ui.column(this);
        LinearLayout nameRow = Ui.row(this);
        nameRow.addView(Ui.text(this, "Shizuku", 16f, Ui.TEXT, Ui.SEMIBOLD));
        szDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Ui.TEXT3);
        szDot.setBackground(dot);
        nameRow.addView(szDot, Ui.lp(Ui.dp(8), Ui.dp(8), Ui.dp(8), 0, 0, 0));
        texts.addView(nameRow);
        szStatus = Ui.text(this, "", 13f, Ui.TEXT2, Ui.REGULAR);
        texts.addView(szStatus, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(2), 0, 0));
        LinearLayout.LayoutParams tp = Ui.lp(0, Ui.WRAP, 1f);
        tp.leftMargin = Ui.dp(12);
        tp.rightMargin = Ui.dp(10);
        row.addView(texts, tp);
        szButton = Ui.text(this, "", 13.5f, Ui.TEXT, Ui.SEMIBOLD);
        szButton.setGravity(Gravity.CENTER);
        szButton.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        szButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shizukuAction();
            }
        });
        row.addView(szButton, Ui.lp(Ui.WRAP, Ui.dp(38)));
        card.addView(row);

        TextView why = Ui.text(this, "Sin root. Con Shizuku, Pulso puede:", 13.5f, Ui.TEXT2, Ui.REGULAR);
        card.addView(why, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(16), 0, Ui.dp(4)));
        String[] items = {
                "Limitar de verdad los FPS en Equilibrado y Eco, y fijar 120 Hz en Modo Gamer",
                "Leer la temperatura de la CPU y la GPU",
                "Medir los FPS reales del juego",
                "Mejora táctil, modo noche y liberar RAM desde el panel",
        };
        for (String s : items) card.addView(bullet(s));

        szSteps = Ui.text(this, "Cómo activarlo: instala Shizuku, ábrelo y usa «Emparejar» con la depuración "
                + "inalámbrica (Opciones de desarrollador). Luego pulsa «Iniciar» y vuelve aquí para dar permiso a Pulso. "
                + "Tras reiniciar el móvil hay que volver a pulsar «Iniciar» en Shizuku.", 12.5f, Ui.TEXT3, Ui.REGULAR);
        szSteps.setLineSpacing(Ui.dpf(2), 1f);
        card.addView(szSteps, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(12), 0, 0));
        content.addView(card, cardLp());
    }

    private void buildPermissions() {
        LinearLayout card = card("Permisos");
        pOverlay = new PermRow(card, R.drawable.ic_layers, "Mostrar sobre otras apps", "Necesario para el tirador y el panel",
                Sys.settings(this, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true));
        pNotif = new PermRow(card, R.drawable.ic_bell, "Notificaciones", "Aviso fijo con los modos, sin sonido", null);
        pUsage = new PermRow(card, R.drawable.ic_chart, "Acceso a uso", "Para mostrar el nombre del juego y avisarle del modo",
                Sys.settings(this, Settings.ACTION_USAGE_ACCESS_SETTINGS, true));
        pWrite = new PermRow(card, R.drawable.ic_sun, "Modificar ajustes", "Para «Brillo fijo» (no hace falta con Shizuku)",
                Sys.settings(this, Settings.ACTION_MANAGE_WRITE_SETTINGS, true));
        content.addView(card, cardLp());
    }

    private void buildHandle() {
        LinearLayout card = card("Tirador");
        card.addView(label("Lado"));
        sideSeg = new Segmented(this, "Izquierda", "Derecha");
        sideSeg.setListener(new Segmented.Listener() {
            @Override
            public void onSelect(Segmented s, int index) {
                Prefs.put(MainActivity.this, Prefs.SIDE, index);
            }
        });
        card.addView(sideSeg, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(8), 0, Ui.dp(14)));

        posVal = valueLabel();
        card.addView(labelRow("Posición", posVal));
        posSlider = new Slider(this);
        posSlider.setListener(new Slider.Listener() {
            @Override
            public void onChange(Slider s, float v, boolean done) {
                Prefs.put(MainActivity.this, Prefs.POS, 0.1f + v * 0.8f);
                updateSliderLabels();
            }
        });
        card.addView(posSlider, Ui.lp(Ui.MATCH, Ui.WRAP, -Ui.dp(4), 0, -Ui.dp(4), Ui.dp(6)));

        sizeVal = valueLabel();
        card.addView(labelRow("Tamaño", sizeVal));
        sizeSlider = new Slider(this);
        sizeSlider.setListener(new Slider.Listener() {
            @Override
            public void onChange(Slider s, float v, boolean done) {
                int dp = Math.round((70f + v * 150f) / 5f) * 5;
                Prefs.put(MainActivity.this, Prefs.SIZE, dp);
                updateSliderLabels();
            }
        });
        card.addView(sizeSlider, Ui.lp(Ui.MATCH, Ui.WRAP, -Ui.dp(4), 0, -Ui.dp(4), Ui.dp(6)));

        alphaVal = valueLabel();
        card.addView(labelRow("Visibilidad", alphaVal));
        alphaSlider = new Slider(this);
        alphaSlider.setListener(new Slider.Listener() {
            @Override
            public void onChange(Slider s, float v, boolean done) {
                Prefs.put(MainActivity.this, Prefs.ALPHA, v);
                updateSliderLabels();
            }
        });
        card.addView(alphaSlider, Ui.lp(Ui.MATCH, Ui.WRAP, -Ui.dp(4), 0, -Ui.dp(4), Ui.dp(6)));

        tapSw = new ToggleSwitch(this);
        tapSw.setListener(new ToggleSwitch.Listener() {
            @Override
            public void onChanged(ToggleSwitch s, boolean on) {
                Prefs.put(MainActivity.this, Prefs.TAP, on);
            }
        });
        card.addView(switchRow("Abrir también con un toque", "Además de deslizando", tapSw));

        TextView tip = Ui.text(this, "Mantén pulsado el tirador y arrástralo para moverlo.", 12.5f, Ui.TEXT3, Ui.REGULAR);
        card.addView(tip, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(10), 0, 0));
        content.addView(card, cardLp());
    }

    private void buildHud() {
        LinearLayout card = card("HUD de rendimiento");
        hudSw = new ToggleSwitch(this);
        hudSw.setListener(new ToggleSwitch.Listener() {
            @Override
            public void onChanged(ToggleSwitch s, boolean on) {
                Prefs.put(MainActivity.this, Prefs.HUD, on);
            }
        });
        card.addView(switchRow("Mostrar HUD", "FPS, temperatura, consumo y batería siempre a la vista", hudSw));
        card.addView(label("Posición"), Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(14), 0, 0));
        hudSeg = new Segmented(this, "Izquierda", "Centro", "Derecha");
        hudSeg.setListener(new Segmented.Listener() {
            @Override
            public void onSelect(Segmented s, int index) {
                Prefs.put(MainActivity.this, Prefs.HUD_POS, index);
            }
        });
        card.addView(hudSeg, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(8), 0, 0));
        content.addView(card, cardLp());
    }

    private void buildGeneral() {
        LinearLayout card = card("General");
        awakeSw = new ToggleSwitch(this);
        awakeSw.setListener(new ToggleSwitch.Listener() {
            @Override
            public void onChanged(ToggleSwitch s, boolean on) {
                Prefs.put(MainActivity.this, Prefs.AWAKE, on);
            }
        });
        card.addView(switchRow("No apagar la pantalla", "Mientras el panel esté activo", awakeSw));
        edgeSw = new ToggleSwitch(this);
        edgeSw.setListener(new ToggleSwitch.Listener() {
            @Override
            public void onChanged(ToggleSwitch s, boolean on) {
                Prefs.put(MainActivity.this, Prefs.EDGE, on);
            }
        });
        card.addView(switchRow("Borde de luz en Modo Gamer", "Neón alrededor de la pantalla mientras juegas", edgeSw),
                Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(14), 0, 0));
        bootSw = new ToggleSwitch(this);
        bootSw.setListener(new ToggleSwitch.Listener() {
            @Override
            public void onChanged(ToggleSwitch s, boolean on) {
                Prefs.put(MainActivity.this, Prefs.BOOT, on);
            }
        });
        View r2 = switchRow("Iniciar al encender", "Si estaba activo, vuelve solo tras reiniciar", bootSw);
        card.addView(r2, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(14), 0, 0));
        content.addView(card, cardLp());
    }

    private TextView diagText;

    private void buildDiag() {
        LinearLayout card = card("Cierres de la app");
        diagText = Ui.text(this, "", 13f, Ui.TEXT2, Ui.REGULAR);
        diagText.setLineSpacing(Ui.dpf(2), 1f);
        card.addView(diagText);
        TextView copy = Ui.text(this, "Copiar informe", 14f, Ui.TEXT, Ui.SEMIBOLD);
        copy.setGravity(Gravity.CENTER);
        copy.setBackground(Ui.ripple(Ui.rect(0x1AFFFFFF, 14, 0x24FFFFFF), 14, 0x33FFFFFF));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Informe de Pulso", Diag.report(MainActivity.this)));
                Sys.toast(MainActivity.this, "Informe copiado: pégaselo a Claude");
            }
        });
        card.addView(copy, Ui.lp(Ui.MATCH, Ui.dp(42), 0, Ui.dp(12), 0, 0));
        content.addView(card, cardLp());
    }

    // ---------------------------------------------------------------- piezas

    private LinearLayout card(String title) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rect(Ui.CARD, 24, Ui.CARD_STROKE));
        int p = Ui.dp(18);
        card.setPadding(p, p, p, p);
        if (title != null) {
            TextView t = Ui.text(this, title.toUpperCase(Ui.ES), 11.5f, Ui.TEXT3, Ui.SEMIBOLD);
            t.setLetterSpacing(0.1f);
            card.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, Ui.dp(14)));
        }
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        return Ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, Ui.dp(14));
    }

    private TextView label(String s) {
        return Ui.text(this, s, 14.5f, Ui.TEXT, Ui.MEDIUM);
    }

    private TextView valueLabel() {
        TextView t = Ui.text(this, "", 13.5f, Ui.TEXT2, Ui.MEDIUM);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    private View labelRow(String title, TextView value) {
        LinearLayout row = Ui.row(this);
        row.addView(label(title), Ui.lp(0, Ui.WRAP, 1f));
        row.addView(value, Ui.lp(Ui.WRAP, Ui.WRAP));
        return row;
    }

    private View switchRow(String title, String sub, ToggleSwitch sw) {
        LinearLayout row = Ui.row(this);
        LinearLayout texts = Ui.column(this);
        texts.addView(label(title));
        TextView s = Ui.text(this, sub, 12.5f, Ui.TEXT2, Ui.REGULAR);
        texts.addView(s, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(2), 0, 0));
        LinearLayout.LayoutParams tp = Ui.lp(0, Ui.WRAP, 1f);
        tp.rightMargin = Ui.dp(12);
        row.addView(texts, tp);
        row.addView(sw, Ui.lp(Ui.WRAP, Ui.WRAP));
        return row;
    }

    private View iconBadge(int icon) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(Ui.icon(this, icon, Ui.TEXT));
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        v.setBackground(Ui.rect(0x14FFFFFF, 13, Ui.TILE_STROKE));
        return v;
    }

    private View bullet(String text) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        ImageView check = new ImageView(this);
        check.setImageDrawable(Ui.icon(this, R.drawable.ic_check, Ui.GOOD));
        row.addView(check, Ui.lp(Ui.dp(16), Ui.dp(16), 0, Ui.dp(3), 0, 0));
        TextView t = Ui.text(this, text, 13.5f, Ui.TEXT, Ui.REGULAR);
        LinearLayout.LayoutParams p = Ui.lp(0, Ui.WRAP, 1f);
        p.leftMargin = Ui.dp(10);
        row.addView(t, p);
        LinearLayout wrap = Ui.column(this);
        wrap.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(6), 0, 0));
        return wrap;
    }

    /** Fila de permiso: icono, texto y un botón «Conceder» que pasa a «✓» cuando ya está. */
    private final class PermRow {
        final TextView chip;
        final Intent intent;

        PermRow(LinearLayout parent, int icon, String title, String sub, Intent intent) {
            this.intent = intent;
            LinearLayout row = Ui.row(MainActivity.this);
            row.addView(iconBadge(icon), Ui.lp(Ui.dp(40), Ui.dp(40)));
            LinearLayout texts = Ui.column(MainActivity.this);
            texts.addView(Ui.text(MainActivity.this, title, 15f, Ui.TEXT, Ui.MEDIUM));
            texts.addView(Ui.text(MainActivity.this, sub, 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.lp(Ui.MATCH, Ui.WRAP, 0, Ui.dp(2), 0, 0));
            LinearLayout.LayoutParams tp = Ui.lp(0, Ui.WRAP, 1f);
            tp.leftMargin = Ui.dp(12);
            tp.rightMargin = Ui.dp(10);
            row.addView(texts, tp);
            chip = Ui.text(MainActivity.this, "", 13f, Ui.TEXT, Ui.SEMIBOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
            chip.setMinWidth(Ui.dp(44));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    act();
                }
            });
            row.addView(chip, Ui.lp(Ui.WRAP, Ui.dp(34)));
            boolean first = parent.getChildCount() <= 1;
            parent.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, 0, first ? 0 : Ui.dp(14), 0, 0));
        }

        void set(boolean granted) {
            chip.setEnabled(!granted);
            if (granted) {
                chip.setText("✓");
                chip.setTextColor(Ui.GOOD);
                chip.setBackground(Ui.rect(Ui.alpha(Ui.GOOD, 0.12f), 12, Ui.alpha(Ui.GOOD, 0.3f)));
            } else {
                chip.setText("Conceder");
                chip.setTextColor(Ui.TEXT);
                chip.setBackground(Ui.ripple(Ui.rect(0x1AFFFFFF, 12, 0x24FFFFFF), 12, 0x33FFFFFF));
            }
        }

        void act() {
            if (intent == null) { // notificaciones
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                        || !Prefs.get(MainActivity.this).getBoolean("notif_asked", false)) {
                    Prefs.put(MainActivity.this, "notif_asked", true);
                    requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                } else {
                    Sys.open(MainActivity.this, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
                return;
            }
            try {
                startActivity(intent);
            } catch (Exception e) {
                // Algunos ajustes no aceptan el paquete: se abre la lista general.
                Sys.open(MainActivity.this, new Intent(intent.getAction()));
            }
        }
    }
}
