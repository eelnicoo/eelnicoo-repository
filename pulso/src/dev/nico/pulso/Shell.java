package dev.nico.pulso;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import moe.shizuku.server.IRemoteProcess;

/**
 * Una shell «sh» persistente a través de Shizuku. Cada orden se escribe en su entrada
 * seguida de un marcador, y se lee la salida hasta el marcador. Así no se crea un
 * proceso nuevo cada segundo para leer FPS y temperaturas.
 * Nunca llamar a run() desde el hilo principal.
 */
final class Shell {
    private Shell() {}

    private static final String END = "__PULSO_END__";
    private static final String EOF = new String("\u0000EOF");
    private static final Object LOCK = new Object();
    private static volatile Session session;

    private static final class Session {
        final IRemoteProcess proc;
        final OutputStream stdin;
        final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
        volatile boolean dead;

        Session(IRemoteProcess p) throws Exception {
            proc = p;
            stdin = new ParcelFileDescriptor.AutoCloseOutputStream(p.getOutputStream());
            final InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(p.getInputStream());
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        String line;
                        while ((line = r.readLine()) != null) lines.offer(line);
                    } catch (IOException ignored) {
                    } finally {
                        dead = true;
                        lines.offer(EOF);
                    }
                }
            }, "pulso-shell");
            reader.setDaemon(true);
            reader.start();
            // Lo que la propia shell escriba en stderr va también a stdout: así nunca se llena una tubería sin leer.
            stdin.write("exec 2>&1\n".getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        void close() {
            dead = true;
            try {
                proc.destroy();
            } catch (Throwable ignored) {
            }
            try {
                stdin.close();
            } catch (Throwable ignored) {
            }
            lines.offer(EOF);
        }
    }

    /** Ejecuta una orden y devuelve su salida, o null si Shizuku no está listo o tarda demasiado. */
    static String run(String cmd, long timeoutMs) {
        synchronized (LOCK) {
            if (!Shizuku.ready()) return null;
            Session s = session;
            try {
                if (s == null || s.dead) {
                    s = new Session(Shizuku.newProcess(new String[] {"sh"}));
                    session = s;
                }
                s.lines.clear();
                s.stdin.write((cmd + "\necho " + END + "\n").getBytes(StandardCharsets.UTF_8));
                s.stdin.flush();
                StringBuilder out = new StringBuilder();
                long deadline = SystemClock.uptimeMillis() + timeoutMs;
                while (true) {
                    long left = deadline - SystemClock.uptimeMillis();
                    String line = left > 0 ? s.lines.poll(left, TimeUnit.MILLISECONDS) : null;
                    if (line == null || line == EOF) {
                        s.close();
                        if (session == s) session = null;
                        return null;
                    }
                    int i = line.indexOf(END);
                    if (i >= 0) {
                        if (i > 0) out.append(line, 0, i).append('\n');
                        return out.toString();
                    }
                    out.append(line).append('\n');
                }
            } catch (Throwable t) {
                Log.w("Pulso", "shell: " + t);
                if (s != null) s.close();
                session = null;
                return null;
            }
        }
    }

    /** Ejecuta en segundo plano, sin esperar al resultado. */
    static void async(final String cmd) {
        Sys.BG.execute(new Runnable() {
            @Override
            public void run() {
                Shell.run(cmd, 8000);
            }
        });
    }

    /** Cierra la shell (Shizuku se ha reiniciado o desconectado). No bloquea. */
    static void reset() {
        Session s = session;
        session = null;
        if (s != null) s.close();
    }
}
