package dev.nico.pulso;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.view.Display;
import android.view.WindowManager;

/**
 * Servicio en primer plano que mantiene el tirador, el panel y el HUD.
 * Su notificación (silenciosa, sin icono en la barra) permite cambiar de modo.
 */
public class PulsoService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener, Shizuku.Listener, EdgeHandle.Callback {

    static final String ACTION_STOP = "dev.nico.pulso.action.STOP";
    static final String ACTION_MODE = "dev.nico.pulso.action.MODE";
    static final String ACTION_OPEN = "dev.nico.pulso.action.OPEN";
    static final String EXTRA_MODE = "mode";
    private static final String CHANNEL = "pulso";
    private static final int NOTIF_ID = 1;

    static volatile boolean running;

    private boolean started;
    private Context wctx;
    private SharedPreferences prefs;
    private StatsEngine stats;
    private EdgeHandle handle;
    private GamePanel panel;
    private Hud hud;
    private ComponentCallbacks callbacks;

    static void start(Context c) {
        Prefs.put(c, Prefs.ENABLED, true);
        c.startForegroundService(new Intent(c, PulsoService.class));
    }

    static void stop(Context c) {
        Prefs.put(c, Prefs.ENABLED, false);
        c.stopService(new Intent(c, PulsoService.class));
    }

    StatsEngine stats() {
        return stats;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        // Si Android reinicia el servicio tras cerrarlo (p. ej. por falta de memoria), puede no dejar
        // pasar a primer plano hasta que haya una ventana visible: entonces se pone antes el tirador.
        boolean foreground = goForeground();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        started = true;
        running = true;
        prefs = Prefs.get(this);
        Display display = Sys.display(this);
        wctx = createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        stats = new StatsEngine(this);
        handle = new EdgeHandle(wctx, this);
        panel = new GamePanel(this, wctx);
        hud = new Hud(wctx, stats);

        Mode m = Prefs.mode(this);
        applyModeUi(m);
        handle.setKeepScreenOn(Prefs.awake(this));
        handle.attach();
        hud.setVisible(Prefs.hud(this));

        prefs.registerOnSharedPreferenceChangeListener(this);
        Shizuku.addListener(this);
        callbacks = new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                Sys.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        onScreenChanged();
                    }
                });
            }

            @Override
            public void onLowMemory() {
            }
        };
        wctx.registerComponentCallbacks(callbacks);
        ModeController.apply(this, m);
        requestTileUpdate();
        if (!foreground) {
            Sys.MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (started && !goForeground()) stopSelf();
                }
            }, 500);
        }
    }

    private boolean goForeground() {
        try {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            return true;
        } catch (RuntimeException e) {
            android.util.Log.w("Pulso", "startForeground", e);
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!started) return START_NOT_STICKY;
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            Prefs.put(this, Prefs.ENABLED, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_MODE.equals(action)) {
            ModeController.select(this, Mode.from(intent.getStringExtra(EXTRA_MODE)), null, true);
        } else if (ACTION_OPEN.equals(action)) {
            panel.open();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (started) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            Shizuku.removeListener(this);
            try {
                wctx.unregisterComponentCallbacks(callbacks);
            } catch (RuntimeException ignored) {
            }
            panel.destroy();
            hud.setVisible(false);
            handle.detach();
            stats.shutdown();
            if (!Prefs.enabled(this)) {
                ModeController.restore(this);
                Touch.restore(this);
            }
        }
        requestTileUpdate();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onOpenPanel() {
        panel.open();
    }

    private void onScreenChanged() {
        if (!started) return;
        handle.relayout();
        hud.relayout();
        if (panel.isOpen()) panel.close(); // girar con el panel abierto: se cierra y se vuelve a abrir colocado
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key == null || !started) return;
        switch (key) {
            case Prefs.SIDE:
                handle.relayout();
                updateNotification();
                break;
            case Prefs.POS:
            case Prefs.SIZE:
            case Prefs.ALPHA:
            case Prefs.TAP:
                handle.relayout();
                break;
            case Prefs.HUD:
                hud.setVisible(Prefs.hud(this));
                break;
            case Prefs.HUD_POS:
                hud.relayout();
                break;
            case Prefs.ECO_FPS:
                if (Prefs.mode(this) == Mode.ECO) ModeController.apply(this, Mode.ECO);
                break;
            case Prefs.AWAKE:
                handle.setKeepScreenOn(Prefs.awake(this));
                break;
            case Prefs.MODE: {
                Mode m = Prefs.mode(this);
                applyModeUi(m);
                panel.onModeChanged(m);
                updateNotification();
                requestTileUpdate();
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onShizukuChanged() {
        if (!started) return;
        Mode m = Prefs.mode(this);
        applyModeUi(m);
        if (Shizuku.ready()) ModeController.apply(this, m);
    }

    /** Color del tirador y del HUD, y la frecuencia preferida si no hay Shizuku. */
    private void applyModeUi(Mode m) {
        handle.setAccent(m.c1);
        hud.setAccent(m.c1);
        if (Shizuku.ready() || m == Mode.BALANCED) {
            handle.setRefreshHint(0, 0f);
        } else if (m == Mode.PERFORMANCE) {
            float hz = Sys.maxRefresh(this);
            handle.setRefreshHint(Sys.modeIdFor(this, hz), hz);
        } else {
            handle.setRefreshHint(Sys.modeIdFor(this, 60f), 60f);
        }
    }

    // ---------------------------------------------------------------- notificación

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Panel activo", NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("Aviso fijo mientras el panel de juego está activo. Desde aquí se cambia de modo.");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Mode m = Prefs.mode(this);
        PendingIntent content = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String edge = Prefs.side(this) == 0 ? "izquierdo" : "derecho";
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Modo " + m.label)
                .setContentText("Desliza desde el borde " + edge + " para abrir el panel")
                .setColor(m.c1)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(content)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        // Android enseña como mucho 3 botones: los modos a los que se puede cambiar.
        Mode[] all = Mode.available(Sys.maxRefresh(this));
        int shown = 0;
        for (int i = 0; i < all.length && shown < 3; i++) {
            Mode x = all[i];
            if (x == m && all.length > 3) continue;
            shown++;
            Intent it = new Intent(this, PulsoService.class).setAction(ACTION_MODE).putExtra(EXTRA_MODE, x.name());
            PendingIntent pi = PendingIntent.getService(this, 10 + i, it,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String label = x == m ? "● " + x.label : x.label;
            b.addAction(new Notification.Action.Builder(Icon.createWithResource(this, x.icon), label, pi).build());
        }
        return b.build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification());
    }

    private void requestTileUpdate() {
        try {
            TileService.requestListeningState(this, new ComponentName(this, PulsoTile.class));
        } catch (RuntimeException ignored) {
        }
    }
}
