package dev.nico.pulso;

/** Los tres modos del panel. Ninguno activa el ahorro de batería del sistema. */
enum Mode {
    PERFORMANCE("Rendimiento", R.drawable.ic_bolt, 0xFFFF8A3D, 0xFFFF2F6D),
    BALANCED("Equilibrado", R.drawable.ic_balance, 0xFF3FD7FF, 0xFF6D7CFF),
    SAVER("Ahorro", R.drawable.ic_leaf, 0xFF5FF1B5, 0xFF16BE8B);

    final String label;
    final int icon;
    final int c1;
    final int c2;

    Mode(String label, int icon, int c1, int c2) {
        this.label = label;
        this.icon = icon;
        this.c1 = c1;
        this.c2 = c2;
    }

    static Mode from(String name) {
        if (name != null) {
            for (Mode m : values()) {
                if (m.name().equals(name)) return m;
            }
        }
        return BALANCED;
    }

    String describe(boolean shizuku, float maxHz) {
        String hz = Ui.f0(maxHz);
        if (maxHz < 61f) { // pantalla fija a 60 Hz (p. ej. la Tab S7 FE): solo cambia el modo de juego
            if (this == PERFORMANCE) return "El juego en modo rendimiento. Esta pantalla va siempre a 60 Hz.";
            if (this == SAVER) return "El juego en modo ahorro, sin activar el ahorro de batería. Esta pantalla va siempre a 60 Hz.";
            return "Lo decide el sistema, como siempre.";
        }
        if (this == PERFORMANCE) {
            return shizuku
                    ? "Pantalla fija a " + hz + " Hz y el juego en modo rendimiento."
                    : "Pantalla a " + hz + " Hz mientras Pulso esté activo.";
        }
        if (this == SAVER) {
            return shizuku
                    ? "Pantalla y juegos limitados a 60 FPS, sin activar el ahorro de batería."
                    : "Pide 60 Hz al sistema. Con Shizuku, el límite de 60 FPS es exacto.";
        }
        return "La frecuencia la decide el sistema, como siempre.";
    }
}
