package dev.nico.pulso;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Mosaico de ajustes rápidos: enciende y apaga el panel. */
public class PulsoTile extends TileService {
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        if (PulsoService.running) {
            PulsoService.stop(this);
        } else if (!Settings.canDrawOverlays(this)) {
            openApp();
            return;
        } else {
            try {
                PulsoService.start(this);
            } catch (RuntimeException e) {
                openApp();
                return;
            }
        }
        Sys.MAIN.postDelayed(refresh, 350);
    }

    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = PulsoService.running;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Pulso");
        t.setSubtitle(on ? Prefs.mode(this).label : "Apagado");
        t.updateTile();
    }
}
