package dev.nico.pulso;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.regex.Pattern;

/**
 * «Mejora táctil» (con Shizuku): activa la mayor sensibilidad táctil del Pixel si el sistema la
 * tiene (ajustes touch_sensitivity* / screen_protector*) y pone las animaciones del sistema a ×0,5.
 * Guarda los valores originales y los restaura al apagarla o al desactivar Pulso.
 * La frecuencia de muestreo del táctil no se puede cambiar sin root.
 */
final class Touch {
    private Touch() {}

    static final String ON = "touch";
    private static final String SAVED = "touch_saved";
    private static final String ANIM = "touch_anim";   // los tres valores originales, separados por «;»
    private static final String KEYS = "touch_keys";   // «clave=valor;…» de sensibilidad táctil
    private static final String[] SCALES = {"window_animation_scale", "transition_animation_scale", "animator_duration_scale"};
    private static final Pattern VALUE = Pattern.compile("[0-9]+(\\.[0-9]+)?|null");
    private static final Pattern KEY = Pattern.compile("[a-z_]+");

    static boolean on(Context c) {
        return Prefs.get(c).getBoolean(ON, false);
    }

    /** Cambia el estado en segundo plano y luego llama a done (en el hilo principal). */
    static void set(Context c, final boolean on, final Runnable done) {
        final Context app = c.getApplicationContext();
        Sys.BG.execute(new Runnable() {
            @Override
            public void run() {
                apply(app, on);
                if (done != null) Sys.MAIN.post(done);
            }
        });
    }

    static void restore(Context c) {
        set(c, false, null);
    }

    private static void apply(Context c, boolean on) {
        if (!Shizuku.ready()) return;
        SharedPreferences p = Prefs.get(c);
        StringBuilder cmd = new StringBuilder();
        if (on) {
            if (!p.getBoolean(SAVED, false)) {
                String out = Shell.run("settings get global window_animation_scale; settings get global transition_animation_scale;"
                        + " settings get global animator_duration_scale;"
                        + " settings list secure | grep -iE '^(touch_sensitivity[a-z_]*|screen_protector[a-z_]*)=' | head -n 3", 5000);
                if (out == null) return;
                String[] l = out.trim().split("\n");
                if (l.length < 3) return;
                StringBuilder anim = new StringBuilder(), keys = new StringBuilder();
                for (int i = 0; i < 3; i++) anim.append(l[i].trim()).append(';');
                for (int i = 3; i < l.length; i++) {
                    String kv = l[i].trim();
                    int eq = kv.indexOf('=');
                    if (eq > 0 && KEY.matcher(kv.substring(0, eq)).matches()) keys.append(kv).append(';');
                }
                p.edit().putString(ANIM, anim.toString()).putString(KEYS, keys.toString()).putBoolean(SAVED, true).apply();
            }
            String[] orig = p.getString(ANIM, "").split(";");
            for (int i = 0; i < SCALES.length; i++) {
                float v = 1f;
                try {
                    v = Float.parseFloat(i < orig.length ? orig[i] : "1");
                } catch (NumberFormatException ignored) {
                }
                if (v > 0.5f) cmd.append("settings put global ").append(SCALES[i]).append(" 0.5; ");
            }
            for (String kv : p.getString(KEYS, "").split(";")) {
                int eq = kv.indexOf('=');
                if (eq > 0) cmd.append("settings put secure ").append(kv.substring(0, eq)).append(" 1; ");
            }
        } else {
            if (!p.getBoolean(SAVED, false)) {
                p.edit().putBoolean(ON, false).apply();
                return;
            }
            String[] orig = p.getString(ANIM, "").split(";");
            for (int i = 0; i < SCALES.length; i++) {
                String v = i < orig.length ? orig[i] : "null";
                if (!VALUE.matcher(v).matches() || v.equals("null")) cmd.append("settings delete global ").append(SCALES[i]).append(" >/dev/null; ");
                else cmd.append("settings put global ").append(SCALES[i]).append(' ').append(v).append("; ");
            }
            for (String kv : p.getString(KEYS, "").split(";")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String v = kv.substring(eq + 1);
                if (VALUE.matcher(v).matches() && !v.equals("null")) {
                    cmd.append("settings put secure ").append(kv.substring(0, eq)).append(' ').append(v).append("; ");
                }
            }
        }
        cmd.append("true");
        if (Shell.run(cmd.toString(), 6000) == null) return;
        SharedPreferences.Editor e = p.edit().putBoolean(ON, on);
        if (!on) e.putBoolean(SAVED, false);
        e.apply();
    }
}
