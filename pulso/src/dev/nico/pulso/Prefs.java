package dev.nico.pulso;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes guardados. El servicio escucha los cambios y recoloca el tirador o el HUD al momento. */
final class Prefs {
    private Prefs() {}

    static final String MODE = "mode";
    static final String ENABLED = "enabled";
    static final String SIDE = "side";          // 0 izquierda, 1 derecha
    static final String POS = "pos";            // centro del tirador, 0..1 de la altura
    static final String SIZE = "size";          // alto del tirador en dp
    static final String ALPHA = "alpha";        // visibilidad del tirador, 0..1
    static final String TAP = "tap";            // abrir también con un toque
    static final String HUD = "hud";
    static final String HUD_POS = "hud_pos";    // 0 izquierda, 1 centro, 2 derecha
    static final String AWAKE = "awake";        // no apagar la pantalla
    static final String BOOT = "boot";
    // Valores originales de la frecuencia, para dejarlo todo como estaba.
    static final String ORIG_SAVED = "orig_saved";
    static final String ORIG_PEAK = "orig_peak";
    static final String ORIG_MIN = "orig_min";
    static final String GAMES = "games";        // juegos a los que se les cambió el modo de juego

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences("pulso", Context.MODE_PRIVATE);
    }

    static Mode mode(Context c) {
        return Mode.from(get(c).getString(MODE, null));
    }

    static boolean enabled(Context c) {
        return get(c).getBoolean(ENABLED, false);
    }

    static int side(Context c) {
        return get(c).getInt(SIDE, 0);
    }

    static float pos(Context c) {
        return get(c).getFloat(POS, 0.34f);
    }

    static int size(Context c) {
        return get(c).getInt(SIZE, 120);
    }

    static float alpha(Context c) {
        return get(c).getFloat(ALPHA, 0.6f);
    }

    static boolean tap(Context c) {
        return get(c).getBoolean(TAP, false);
    }

    static boolean hud(Context c) {
        return get(c).getBoolean(HUD, false);
    }

    static int hudPos(Context c) {
        return get(c).getInt(HUD_POS, 0);
    }

    static boolean awake(Context c) {
        return get(c).getBoolean(AWAKE, false);
    }

    static boolean boot(Context c) {
        return get(c).getBoolean(BOOT, true);
    }

    static void put(Context c, String key, boolean v) {
        get(c).edit().putBoolean(key, v).apply();
    }

    static void put(Context c, String key, int v) {
        get(c).edit().putInt(key, v).apply();
    }

    static void put(Context c, String key, float v) {
        get(c).edit().putFloat(key, v).apply();
    }

    static void setMode(Context c, Mode m) {
        get(c).edit().putString(MODE, m.name()).apply();
    }
}
