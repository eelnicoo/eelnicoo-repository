package dev.nico.pulso;

/** Los tres modos del panel. Ninguno activa el ahorro de batería del sistema. */
enum Mode {
    PERFORMANCE("Rendimiento", R.drawable.ic_bolt, 0xFFFF8A3D, 0xFFFF2F6D),
    BALANCED("Equilibrado", R.drawable.ic_balance, 0xFF3FD7FF, 0xFF6D7CFF),
    SAVER("Ahorro", R.drawable.ic_leaf, 0xFF5FF1B5, 0xFF16BE8B),
    ECO("Eco", R.drawable.ic_eco, 0xFFD8F56B, 0xFF86CF45);

    /** FPS máximos de los modos de ahorro. */
    int fpsLimit() {
        return this == ECO ? 30 : 60;
    }

    boolean saving() {
        return this == SAVER || this == ECO;
    }

    /** Modos que tienen sentido en esta pantalla: Eco (30 FPS) solo si va a más de 60 Hz. */
    static Mode[] available(float maxHz) {
        return maxHz >= 61f ? values() : new Mode[] {PERFORMANCE, BALANCED, SAVER};
    }

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
        boolean fixed60 = maxHz < 61f; // pantalla fija a 60 Hz (p. ej. la Tab S7 FE)
        if (this == PERFORMANCE) {
            if (fixed60) return "El juego en modo rendimiento. Esta pantalla va siempre a 60 Hz.";
            return shizuku
                    ? "Pantalla fija a " + hz + " Hz y el juego en modo rendimiento."
                    : "Pantalla a " + hz + " Hz mientras Pulso esté activo.";
        }
        if (this == SAVER) {
            if (fixed60) return "El juego en modo ahorro, sin activar el ahorro de batería. Esta pantalla va siempre a 60 Hz.";
            return shizuku
                    ? "Pantalla y juegos limitados a 60 FPS, sin activar el ahorro de batería."
                    : "Pide 60 Hz al sistema. Con Shizuku, el límite de 60 FPS es exacto.";
        }
        if (this == ECO) {
            return shizuku
                    ? "Juegos a 30 FPS y el juego en modo batería: el procesador trabaja menos y baja de GHz. Sin el ahorro de batería."
                    : "Con Shizuku, limita los juegos a 30 FPS para que el procesador trabaje menos.";
        }
        return "La frecuencia la decide el sistema, como siempre.";
    }
}
