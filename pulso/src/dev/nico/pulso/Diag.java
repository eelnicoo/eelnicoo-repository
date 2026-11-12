package dev.nico.pulso;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Informe de cierres: por qué Android cerró Pulso las últimas veces y el último error, si lo hubo. */
final class Diag {
    private Diag() {}

    static String reason(int r) {
        switch (r) {
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "Falta de memoria";
            case ApplicationExitInfo.REASON_CRASH: return "Error de la app";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "Error nativo";
            case ApplicationExitInfo.REASON_ANR: return "No respondía (ANR)";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "Demasiados recursos";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "Cerrada por el usuario";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "Forzada a detenerse";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "Cambio de permisos";
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED: return "Actualización";
            case ApplicationExitInfo.REASON_SIGNALED: return "Matada por el sistema";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "Se cerró sola";
            case ApplicationExitInfo.REASON_FREEZER: return "Congelada";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "Dependencia cerrada";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "Fallo al iniciar";
            default: return "Otro (" + r + ")";
        }
    }

    /** Resumen corto para la pantalla (los 3 últimos cierres). */
    static String summary(Context c) {
        StringBuilder sb = new StringBuilder();
        List<ApplicationExitInfo> list = exits(c, 3);
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        for (ApplicationExitInfo e : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(f.format(new Date(e.getTimestamp()))).append(" · ").append(reason(e.getReason()));
        }
        return sb.length() == 0 ? "Sin cierres registrados." : sb.toString();
    }

    /** Informe completo para copiar. */
    static String report(Context c) {
        StringBuilder sb = new StringBuilder();
        String version = "?";
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        sb.append("Pulso ").append(version).append(" · ").append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE).append('\n');
        sb.append("Panel activo: ").append(PulsoService.running).append(" · debería estarlo: ").append(Prefs.enabled(c))
                .append(" · Shizuku: ").append(Shizuku.ready()).append('\n');
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            sb.append("RAM libre: ").append(mi.availMem / 1048576).append(" MB de ").append(mi.totalMem / 1048576)
                    .append(" MB · poca memoria: ").append(mi.lowMemory).append('\n');
        }
        sb.append("== cierres\n");
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault());
        for (ApplicationExitInfo e : exits(c, 8)) {
            sb.append(f.format(new Date(e.getTimestamp()))).append(" · ").append(reason(e.getReason()))
                    .append(" · importancia ").append(e.getImportance())
                    .append(" · RSS ").append(e.getRss() / 1024).append(" MB");
            if (e.getDescription() != null) sb.append(" · ").append(e.getDescription());
            sb.append('\n');
        }
        sb.append("== último error\n").append(lastCrash(c));
        return sb.toString();
    }

    private static List<ApplicationExitInfo> exits(Context c, int max) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        try {
            return am.getHistoricalProcessExitReasons(c.getPackageName(), 0, max);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    private static String lastCrash(Context c) {
        File f = new File(c.getFilesDir(), PulsoApp.CRASH_FILE);
        if (!f.exists()) return "Ninguno.\n";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 45) sb.append(line).append('\n');
        } catch (Exception e) {
            sb.append("No se pudo leer.\n");
        }
        return sb.toString();
    }
}
