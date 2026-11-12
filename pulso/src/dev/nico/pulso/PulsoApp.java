package dev.nico.pulso;

import android.app.Application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Guarda el último error que cierre la app, para poder verlo y copiarlo desde la pantalla de Pulso. */
public class PulsoApp extends Application {
    static final String CRASH_FILE = "ultimo-error.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try (PrintWriter w = new PrintWriter(new FileOutputStream(new File(getFilesDir(), CRASH_FILE)))) {
                    w.println(new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(new Date())
                            + " · hilo " + t.getName());
                    e.printStackTrace(w);
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }
}
