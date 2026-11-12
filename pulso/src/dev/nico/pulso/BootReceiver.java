package dev.nico.pulso;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/** Vuelve a activar el panel al encender el móvil o al actualizar la app, si estaba activo. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (!Prefs.enabled(c) || !Settings.canDrawOverlays(c)) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) && !Prefs.boot(c)) return;
        try {
            PulsoService.start(c);
        } catch (RuntimeException e) {
            Log.w("Pulso", "No se pudo iniciar al arrancar", e);
        }
    }
}
