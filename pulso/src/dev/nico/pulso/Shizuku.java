package dev.nico.pulso;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuService;

/**
 * Cliente mínimo de Shizuku (protocolo de la API 13), sin la librería rikka.shizuku:
 * la de Maven usa lambdas, que no funcionan al compilar con dx.
 * Shizuku manda su binder a {@link ShizukuProvider}; aquí se registra la app y se
 * piden permiso y procesos de shell (uid 2000, o root si Shizuku se inició con root).
 */
final class Shizuku {
    private Shizuku() {}

    static final String PACKAGE = "moe.shizuku.privileged.api";
    private static final String TAG = "Pulso";

    interface Listener {
        void onShizukuChanged();
    }

    enum State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    private static volatile IBinder binder;
    private static volatile IShizukuService service;
    private static volatile boolean granted;
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final IBinder.DeathRecipient DEATH = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            onBinderReceived(null, null);
        }
    };

    private static final IShizukuApplication.Stub APP = new IShizukuApplication.Stub() {
        @Override
        public void bindApplication(Bundle data) {
            if (data != null) {
                granted = data.getBoolean("shizuku:attach-reply-permission-granted", granted);
            }
            changed();
        }

        @Override
        public void dispatchRequestPermissionResult(int requestCode, Bundle data) {
            granted = data != null && data.getBoolean("shizuku:request-permission-reply-allowed", false);
            changed();
        }

        @Override
        public void showPermissionConfirmation(int uid, int pid, String pkg, int requestCode) {
            // Solo lo usa Sui; Shizuku muestra su propio diálogo.
        }
    };

    static synchronized void onBinderReceived(IBinder b, String packageName) {
        if (b != null && b == binder) return;
        IBinder old = binder;
        if (old != null) {
            try {
                old.unlinkToDeath(DEATH, 0);
            } catch (Throwable ignored) {
            }
        }
        binder = null;
        service = null;
        granted = false;
        if (b != null && b.pingBinder()) {
            IShizukuService s = IShizukuService.Stub.asInterface(b);
            try {
                b.linkToDeath(DEATH, 0);
            } catch (Throwable ignored) {
            }
            binder = b;
            service = s;
            try {
                Bundle args = new Bundle();
                args.putInt("shizuku:attach-api-version", 13);
                args.putString("shizuku:attach-package-name", packageName);
                s.attachApplication(APP, args);
            } catch (Throwable t) {
                Log.w(TAG, "Shizuku attachApplication", t);
            }
            try {
                granted = s.checkSelfPermission();
            } catch (Throwable ignored) {
            }
        }
        Shell.reset();
        changed();
    }

    static boolean running() {
        IBinder b = binder;
        return b != null && b.pingBinder();
    }

    static boolean ready() {
        return granted && running();
    }

    /** Vuelve a preguntar el permiso (por si se concedió o quitó desde la app de Shizuku). */
    static void refresh() {
        IShizukuService s = service;
        if (s == null) return;
        boolean before = granted;
        try {
            granted = s.checkSelfPermission();
        } catch (Throwable ignored) {
        }
        if (before != granted) changed();
    }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static State state(Context c) {
        if (ready()) return State.READY;
        if (running()) return State.NO_PERMISSION;
        if (installed(c)) return State.NOT_RUNNING;
        return State.NOT_INSTALLED;
    }

    static void requestPermission() {
        IShizukuService s = service;
        if (s == null) return;
        try {
            s.requestPermission(7001);
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku requestPermission", t);
        }
    }

    static IRemoteProcess newProcess(String[] cmd) throws RemoteException {
        IShizukuService s = service;
        if (s == null) throw new RemoteException("Shizuku no está conectado");
        return s.newProcess(cmd, null, null);
    }

    static void addListener(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void changed() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : LISTENERS) l.onShizukuChanged();
            }
        });
    }
}
