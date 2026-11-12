package dev.nico.pulso;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Acceso al sistema: pantalla, app en primer plano, No molestar, brillo y memoria. */
final class Sys {
    private Sys() {}

    static final ExecutorService BG = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "pulso-bg");
            t.setDaemon(true);
            return t;
        }
    });
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Pattern RESUMED = Pattern.compile(" u\\d+ ([A-Za-z0-9_.]+)/");
    static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9_.]+");

    interface LongCallback {
        void done(long value);
    }

    // ---- Pantalla ----

    static Display display(Context c) {
        DisplayManager dm = c.getSystemService(DisplayManager.class);
        return dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
    }

    static float maxRefresh(Display d) {
        Display.Mode cur = d.getMode();
        float max = cur.getRefreshRate();
        for (Display.Mode m : d.getSupportedModes()) {
            if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()) {
                max = Math.max(max, m.getRefreshRate());
            }
        }
        return max;
    }

    static float maxRefresh(Context c) {
        Display d = display(c);
        return d == null ? 60f : maxRefresh(d);
    }

    /** Id del modo de pantalla (misma resolución) con la frecuencia más cercana a hz. */
    static int modeIdFor(Context c, float hz) {
        Display d = display(c);
        if (d == null) return 0;
        Display.Mode cur = d.getMode();
        Display.Mode best = null;
        for (Display.Mode m : d.getSupportedModes()) {
            if (m.getPhysicalWidth() != cur.getPhysicalWidth() || m.getPhysicalHeight() != cur.getPhysicalHeight()) continue;
            if (best == null || Math.abs(m.getRefreshRate() - hz) < Math.abs(best.getRefreshRate() - hz)) best = m;
        }
        return best == null ? 0 : best.getModeId();
    }

    static String thermalName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "Normal";
            case PowerManager.THERMAL_STATUS_LIGHT: return "Templado";
            case PowerManager.THERMAL_STATUS_MODERATE: return "Moderado";
            case PowerManager.THERMAL_STATUS_SEVERE: return "Alto";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "Crítico";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "Extremo";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Apagado";
            default: return "—";
        }
    }

    static int thermalColor(int status) {
        if (status <= PowerManager.THERMAL_STATUS_NONE) return Ui.GOOD;
        if (status <= PowerManager.THERMAL_STATUS_MODERATE) return Ui.WARN;
        return Ui.HOT;
    }

    // ---- App en primer plano ----

    static boolean usageAccess(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Paquete de la app que se está usando (o null si es Pulso, el launcher o no se sabe). Bloquea: no usar en el hilo principal. */
    static String foregroundPackage(Context c) {
        String pkg = null;
        try {
            if (usageAccess(c)) {
                UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
                long now = System.currentTimeMillis();
                UsageEvents ev = usm.queryEvents(now - 3 * 3600_000L, now);
                UsageEvents.Event e = new UsageEvents.Event();
                while (ev != null && ev.hasNextEvent()) {
                    ev.getNextEvent(e);
                    if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) pkg = e.getPackageName();
                }
            } else if (Shizuku.ready()) {
                String out = Shell.run("dumpsys activity activities | grep -m 1 'topResumedActivity='"
                        + " || dumpsys activity activities | grep -m 1 'ResumedActivity'", 3000);
                if (out != null) {
                    Matcher m = RESUMED.matcher(out);
                    if (m.find()) pkg = m.group(1);
                }
            }
        } catch (Throwable t) {
            Log.w("Pulso", "foregroundPackage", t);
        }
        if (pkg == null || pkg.equals(c.getPackageName()) || pkg.equals(homePackage(c))
                || pkg.equals("com.android.systemui") || !PACKAGE_NAME.matcher(pkg).matches()) {
            return null;
        }
        return pkg;
    }

    static String homePackage(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null ? r.activityInfo.packageName : null;
    }

    static CharSequence appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai);
        } catch (Exception e) {
            return null;
        }
    }

    static Drawable appIcon(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- No molestar ----

    static boolean dndOn(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return false;
        int f = nm.getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    static boolean dndAccess(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    /** false si falta el permiso (hay que abrir los ajustes). */
    static boolean setDnd(Context c, boolean on) {
        if (Shizuku.ready()) {
            Shell.async("cmd notification set_dnd " + (on ? "priority" : "off"));
            return true;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_PRIORITY : NotificationManager.INTERRUPTION_FILTER_ALL);
            return true;
        }
        return false;
    }

    // ---- Brillo ----

    static boolean autoBrightness(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    /** false si falta el permiso «Modificar ajustes del sistema». */
    static boolean setAutoBrightness(Context c, boolean auto) {
        int v = auto ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
        if (Settings.System.canWrite(c)) {
            return Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, v);
        }
        if (Shizuku.ready()) {
            Shell.async("settings put system screen_brightness_mode " + v);
            return true;
        }
        return false;
    }

    // ---- Memoria ----

    static long availMem(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am == null) return 0;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    /** Cierra los procesos en segundo plano (am kill-all, con Shizuku) y devuelve los bytes liberados. */
    static void freeRam(final Context c, final LongCallback cb) {
        BG.execute(new Runnable() {
            @Override
            public void run() {
                long before = availMem(c);
                Shell.run("am kill-all", 8000);
                SystemClock.sleep(900);
                final long freed = Math.max(0, availMem(c) - before);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.done(freed);
                    }
                });
            }
        });
    }

    // ---- Ajustes y avisos ----

    static void open(Context c, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            Log.w("Pulso", "open " + i, e);
        }
    }

    static Intent settings(Context c, String action, boolean withPackage) {
        Intent i = new Intent(action);
        if (withPackage) i.setData(Uri.parse("package:" + c.getPackageName()));
        return i;
    }

    static void toast(Context c, String msg) {
        Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
