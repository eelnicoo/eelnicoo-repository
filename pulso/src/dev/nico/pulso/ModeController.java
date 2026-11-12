package dev.nico.pulso;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aplica los modos. Con Shizuku cambia los mismos ajustes que «Pantalla fluida» de Ajustes
 * (peak_refresh_rate y min_refresh_rate), y avisa al juego con la API de modos de juego.
 * Guarda los valores originales y los restaura al volver a Equilibrado o al apagar Pulso.
 * No toca nunca el ahorro de batería.
 * Sin Shizuku, el aviso de frecuencia lo da la ventana del tirador (ver PulsoService).
 */
final class ModeController {
    private ModeController() {}

    private static final Pattern RATE = Pattern.compile("[0-9]+(\\.[0-9]+)?|Infinity");

    /** Cambia el modo elegido por Nico. gamePkg: juego al que avisar (o null). detect: averiguarlo. */
    static void select(Context c, final Mode m, final String gamePkg, final boolean detect) {
        final Context app = c.getApplicationContext();
        Prefs.setMode(app, m);
        Sys.BG.execute(new Runnable() {
            @Override
            public void run() {
                String pkg = gamePkg;
                if (pkg == null && detect) pkg = Sys.foregroundPackage(app);
                applyNow(app, m, pkg);
            }
        });
    }

    /** Vuelve a aplicar el modo guardado (al arrancar o al conectar Shizuku). */
    static void apply(Context c, final Mode m) {
        final Context app = c.getApplicationContext();
        Sys.BG.execute(new Runnable() {
            @Override
            public void run() {
                applyNow(app, m, null);
            }
        });
    }

    /** Deja la pantalla y los juegos como estaban (al apagar Pulso). */
    static void restore(Context c) {
        apply(c, Mode.BALANCED);
    }

    private static void applyNow(Context c, Mode m, String pkg) {
        if (!Shizuku.ready()) return;
        SharedPreferences p = Prefs.get(c);
        StringBuilder cmd = new StringBuilder();

        if (m == Mode.BALANCED) {
            boolean saved = p.getBoolean(Prefs.ORIG_SAVED, false);
            Set<String> games = p.getStringSet(Prefs.GAMES, null);
            if (!saved && (games == null || games.isEmpty())) return;
            if (saved) {
                cmd.append(restoreCmd("peak_refresh_rate", p.getString(Prefs.ORIG_PEAK, null)));
                cmd.append(restoreCmd("min_refresh_rate", p.getString(Prefs.ORIG_MIN, null)));
            }
            if (games != null) {
                for (String g : games) {
                    if (Sys.PACKAGE_NAME.matcher(g).matches()) {
                        cmd.append("cmd game reset ").append(g).append(" >/dev/null 2>&1; ");
                        cmd.append("cmd game mode standard ").append(g).append(" >/dev/null 2>&1; ");
                    }
                }
            }
            cmd.append("true");
            if (Shell.run(cmd.toString(), 8000) != null) {
                p.edit().putBoolean(Prefs.ORIG_SAVED, false).remove(Prefs.GAMES).apply();
            }
            return;
        }

        if (!p.getBoolean(Prefs.ORIG_SAVED, false)) {
            String out = Shell.run("settings get system peak_refresh_rate; settings get system min_refresh_rate", 4000);
            if (out == null) return;
            String[] l = out.trim().split("\n");
            if (l.length < 2) return;
            p.edit().putString(Prefs.ORIG_PEAK, l[0].trim())
                    .putString(Prefs.ORIG_MIN, l[1].trim())
                    .putBoolean(Prefs.ORIG_SAVED, true)
                    .apply();
        }

        if (m == Mode.PERFORMANCE) {
            String hz = rate(Sys.maxRefresh(c));
            cmd.append("settings put system peak_refresh_rate ").append(hz).append("; ");
            cmd.append("settings put system min_refresh_rate ").append(hz).append("; ");
        } else {
            // Desde Android 14, peak_refresh_rate también limita los FPS a los que dibuja el sistema:
            // a 30, los juegos van a 30 aunque el panel siga a 60 Hz.
            int fps = Prefs.ecoFps(c);
            cmd.append("settings put system peak_refresh_rate ").append(rate(fps)).append("; ");
            cmd.append("settings put system min_refresh_rate ").append(saverMin(p.getString(Prefs.ORIG_MIN, null), fps)).append("; ");
        }

        if (pkg != null && Sys.PACKAGE_NAME.matcher(pkg).matches()) {
            // Límite de FPS solo para el juego (modo de juego de Android): sirve donde el ajuste
            // global no baja de los Hz de la pantalla (la tablet, con su panel fijo a 60 Hz).
            if (m.saving()) {
                cmd.append("cmd game set --mode 3 --fps ").append(Prefs.ecoFps(c)).append(' ').append(pkg).append(" >/dev/null 2>&1; ");
            }
            cmd.append("cmd game mode ").append(m == Mode.PERFORMANCE ? "performance " : "battery ")
                    .append(pkg).append(" >/dev/null 2>&1; ");
            Set<String> games = new HashSet<>(p.getStringSet(Prefs.GAMES, new HashSet<String>()));
            games.add(pkg);
            p.edit().putStringSet(Prefs.GAMES, games).apply();
        }
        cmd.append("true");
        Shell.run(cmd.toString(), 8000);
    }

    private static String restoreCmd(String key, String value) {
        if (value == null || !RATE.matcher(value).matches()) {
            return "settings delete system " + key + " >/dev/null 2>&1; ";
        }
        return "settings put system " + key + " " + value + "; ";
    }

    /** En Eco, el mínimo original si no pasa del límite; si no, 0 (si no, el mínimo anularía el límite). */
    private static String saverMin(String orig, int limit) {
        try {
            float v = Float.parseFloat(orig);
            if (v >= 0f && v <= limit) return rate(v);
        } catch (Exception ignored) {
        }
        return "0.0";
    }

    private static String rate(int hz) {
        return rate((float) hz);
    }

    private static String rate(float hz) {
        return String.format(Locale.US, "%.1f", hz);
    }
}
