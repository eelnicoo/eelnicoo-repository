package dev.nico.pulso;

/** Los tres modos del panel (Modo Gamer, Equilibrado y Eco). Ninguno activa el ahorro de batería del sistema. */
enum Mode {
    PERFORMANCE("Modo Gamer", R.drawable.ic_gamepad, 0xFFFF8A3D, 0xFFFF2F6D),
    BALANCED("Equilibrado", R.drawable.ic_balance, 0xFF3FD7FF, 0xFF6D7CFF),
    ECO("Eco", R.drawable.ic_eco, 0xFFD8F56B, 0xFF86CF45);

    boolean saving() {
        return this == ECO;
    }

    static Mode[] available(float maxHz) {
        return values();
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

    String describe(boolean shizuku, float maxHz, int ecoFps, int balFps) {
        String hz = Ui.f0(maxHz);
        boolean fixed60 = maxHz < 61f; // pantalla fija a 60 Hz (p. ej. la Tab S7 FE)
        if (this == PERFORMANCE) {
            if (fixed60) return "El juego en modo rendimiento. Esta pantalla va siempre a 60 Hz.";
            return shizuku
                    ? "Pantalla fija a " + hz + " Hz y el juego en modo rendimiento."
                    : "Pantalla a " + hz + " Hz mientras Pulso esté activo.";
        }
        if (this == ECO) {
            if (fixed60) return "El juego en modo batería, sin activar el ahorro de batería. Esta pantalla no baja de 60 FPS sin root.";
            return shizuku
                    ? "Juegos a " + ecoFps + " FPS y el juego en modo batería: el procesador trabaja menos y baja de GHz. Sin el ahorro de batería."
                    : "Con Shizuku, limita los juegos a " + ecoFps + " FPS para que el procesador trabaje menos.";
        }
        if (!fixed60 && balFps == 60) {
            return shizuku
                    ? "Pantalla y apps hasta 60 FPS; lo demás lo decide el sistema."
                    : "Con Shizuku, limita a 60 FPS. Sin él, lo decide el sistema.";
        }
        return "La frecuencia la decide el sistema, como siempre" + (fixed60 ? "." : " (hasta " + hz + " FPS).");
    }
}
