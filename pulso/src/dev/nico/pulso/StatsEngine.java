package dev.nico.pulso;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee el rendimiento una vez por segundo, solo mientras alguien escucha (panel abierto o HUD).
 * Sin Shizuku: batería, consumo, RAM, frecuencia de pantalla y estado térmico.
 * Con Shizuku: además FPS reales (contador de frames de SurfaceFlinger), carga y
 * frecuencia de la CPU y temperaturas de CPU/GPU del HAL térmico.
 */
final class StatsEngine {
    interface Listener {
        void onStats(Stats s);
    }

    static final int HISTORY = 60;
    private static final Pattern PARCEL = Pattern.compile("Parcel\\(\\s*([0-9a-fA-F]{8})");
    private static final Pattern TEMP = Pattern.compile("Temperature\\{mValue=([-0-9.Ee]+), mType=(-?\\d+), mName=([^,}]*)");

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private HandlerThread thread;
    private volatile Handler bg;
    private volatile boolean active;
    private Stats last;

    // Estado del muestreo: solo se toca en el hilo de fondo.
    private long lastFrames = -1, lastFramesAt;
    private long lastTotal = -1, lastIdle;
    private int tick, flipFails;
    private float headroom = Float.NaN, cpuT = Float.NaN, gpuT = Float.NaN, skinT = Float.NaN;
    private final float[] hFps = nan(), hPow = nan(), hCpu = nan(), hBat = nan();
    private List<String[]> zones;

    StatsEngine(Context c) {
        app = c.getApplicationContext();
    }

    Stats last() {
        return last;
    }

    void add(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        if (last != null) l.onStats(last);
        update();
    }

    void remove(Listener l) {
        listeners.remove(l);
        update();
    }

    void shutdown() {
        listeners.clear();
        active = false;
        if (thread != null) {
            thread.quitSafely();
            thread = null;
            bg = null;
        }
    }

    private void update() {
        boolean want = !listeners.isEmpty();
        if (want == active) return;
        active = want;
        if (want) {
            if (thread == null) {
                thread = new HandlerThread("pulso-stats", Process.THREAD_PRIORITY_BACKGROUND);
                thread.start();
                bg = new Handler(thread.getLooper());
            }
            bg.removeCallbacks(loop);
            bg.post(resetCounters);
            bg.post(loop);
        } else if (bg != null) {
            bg.removeCallbacks(loop);
        }
    }

    private final Runnable resetCounters = new Runnable() {
        @Override
        public void run() {
            lastFrames = -1;
            lastTotal = -1;
        }
    };

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            long start = SystemClock.uptimeMillis();
            Stats s = null;
            try {
                s = sample();
            } catch (Throwable t) {
                Log.w("Pulso", "stats", t);
            }
            if (s != null) {
                final Stats out = s;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        last = out;
                        for (Listener l : listeners) l.onStats(out);
                    }
                });
            }
            Handler h = bg;
            if (active && h != null) h.postAtTime(this, start + 1000);
        }
    };

    private Stats sample() {
        Stats s = new Stats();
        Intent bi = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        if (bi != null) {
            int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) s.battery = Math.round(level * 100f / scale);
            int t = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (t != Integer.MIN_VALUE) s.batteryTemp = t / 10f;
            int mv = bi.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (mv > 0) s.voltage = mv > 100 ? mv / 1000f : mv;
            int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int plugged = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            s.charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0);
        }
        BatteryManager bm = app.getSystemService(BatteryManager.class);
        if (bm != null) {
            int ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (ua != Integer.MIN_VALUE && ua != 0) {
                float a = Math.abs((float) ua);
                if (a < 10000f) a *= 1000f; // algunos móviles lo dan en mA en vez de µA
                s.current = a / 1e6f;
                if (Stats.ok(s.voltage)) s.power = s.current * s.voltage;
            }
        }
        ActivityManager am = app.getSystemService(ActivityManager.class);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.ramTotal = mi.totalMem;
            s.ramUsed = mi.totalMem - mi.availMem;
        }
        Display d = Sys.display(app);
        if (d != null) {
            s.refresh = d.getRefreshRate();
            s.maxRefresh = Sys.maxRefresh(d);
        }
        boolean slow = tick % 2 == 0;
        PowerManager pm = app.getSystemService(PowerManager.class);
        if (pm != null) {
            s.thermal = pm.getCurrentThermalStatus();
            if (slow) {
                float h = pm.getThermalHeadroom(0);
                if (!Float.isNaN(h)) headroom = h;
            }
            s.headroom = headroom;
        }
        s.shizuku = Shizuku.ready();
        if (s.shizuku) {
            privileged(s, slow);
        } else {
            lastFrames = -1;
            lastTotal = -1;
            if (slow) sysfs();
        }
        s.cpuTemp = cpuT;
        s.gpuTemp = gpuT;
        s.skinTemp = skinT;
        tick++;
        push(hFps, s.fps);
        push(hPow, s.power);
        push(hCpu, s.cpuTemp);
        push(hBat, s.batteryTemp);
        s.histFps = hFps.clone();
        s.histPow = hPow.clone();
        s.histCpu = hCpu.clone();
        s.histBat = hBat.clone();
        return s;
    }

    private void privileged(Stats s, boolean slow) {
        boolean flip = flipFails < 3;
        StringBuilder cmd = new StringBuilder();
        cmd.append(flip ? "service call SurfaceFlinger 1013" : "true");
        cmd.append("; echo @@; head -n 1 /proc/stat; echo @@; cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq 2>/dev/null");
        if (slow) cmd.append("; echo @@; dumpsys thermalservice 2>/dev/null");
        String out = Shell.run(cmd.toString(), 2500);
        if (out == null) {
            lastFrames = -1;
            return;
        }
        String[] parts = out.split("@@\n", -1);
        long now = SystemClock.uptimeMillis();
        if (flip) {
            Matcher m = PARCEL.matcher(parts[0]);
            if (m.find()) {
                flipFails = 0;
                long frames = Long.parseLong(m.group(1), 16);
                if (lastFrames >= 0 && frames >= lastFrames) {
                    float dt = (now - lastFramesAt) / 1000f;
                    if (dt > 0.3f) {
                        float cap = s.maxRefresh > 0 ? s.maxRefresh : 240f;
                        s.fps = Math.min((frames - lastFrames) / dt, cap);
                    }
                }
                lastFrames = frames;
                lastFramesAt = now;
            } else {
                flipFails++;
                lastFrames = -1;
            }
        }
        if (parts.length > 1) cpuLoad(s, parts[1]);
        if (parts.length > 2) cpuFreq(s, parts[2]);
        if (slow && parts.length > 3) thermal(parts[3]);
    }

    private void cpuLoad(Stats s, String text) {
        String line = text.trim();
        if (!line.startsWith("cpu")) return;
        String[] f = line.split("\\s+");
        if (f.length < 5) return;
        long total = 0;
        try {
            for (int i = 1; i < f.length; i++) total += Long.parseLong(f[i]);
            long idle = Long.parseLong(f[4]) + (f.length > 5 ? Long.parseLong(f[5]) : 0);
            if (lastTotal >= 0 && total > lastTotal) {
                s.cpuLoad = Ui.clamp01(1f - (idle - lastIdle) / (float) (total - lastTotal));
            }
            lastTotal = total;
            lastIdle = idle;
        } catch (NumberFormatException ignored) {
        }
    }

    private static void cpuFreq(Stats s, String text) {
        long max = 0;
        for (String l : text.split("\n")) {
            try {
                max = Math.max(max, Long.parseLong(l.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (max > 0) s.cpuGhz = max / 1e6f;
    }

    private void thermal(String text) {
        String sec = text;
        int from = text.indexOf("Current temperatures from HAL");
        if (from >= 0) {
            int to = text.indexOf("Current cooling devices", from);
            sec = text.substring(from, to > from ? to : text.length());
        }
        float cpu = Float.NaN, gpu = Float.NaN, skin = Float.NaN, vskin = Float.NaN, soc = Float.NaN;
        Matcher m = TEMP.matcher(sec);
        while (m.find()) {
            float v;
            int type;
            try {
                v = Float.parseFloat(m.group(1));
                type = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            if (!(v > -30f && v < 150f)) continue;
            String name = m.group(3).trim().toUpperCase(Locale.ROOT);
            switch (type) {
                case 0: cpu = max(cpu, v); break;            // TYPE_CPU
                case 1: gpu = max(gpu, v); break;            // TYPE_GPU
                case 3:                                       // TYPE_SKIN
                    if (name.contains("VIRTUAL")) vskin = v;
                    else skin = max(skin, v);
                    break;
                case 13: soc = max(soc, v); break;           // TYPE_SOC
                default: break;
            }
        }
        cpuT = Stats.ok(cpu) ? cpu : soc;
        gpuT = gpu;
        skinT = Stats.ok(vskin) ? vskin : skin;
    }

    /** Sin Shizuku: intenta leer las zonas térmicas de /sys (en muchos móviles SELinux no lo deja). */
    private void sysfs() {
        if (zones == null) {
            zones = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                File dir = new File("/sys/class/thermal/thermal_zone" + i);
                String type = readLine(new File(dir, "type"));
                if (type == null) continue;
                File temp = new File(dir, "temp");
                if (readLine(temp) == null) continue;
                zones.add(new String[] {temp.getPath(), type.toLowerCase(Locale.ROOT)});
            }
        }
        float cpu = Float.NaN, gpu = Float.NaN, skin = Float.NaN;
        for (String[] z : zones) {
            String raw = readLine(new File(z[0]));
            if (raw == null) continue;
            float v;
            try {
                v = Float.parseFloat(raw.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (Math.abs(v) > 1000f) v /= 1000f;
            if (!(v > -30f && v < 150f)) continue;
            String t = z[1];
            if (t.contains("gpu") || t.contains("g3d") || t.contains("mali")) gpu = max(gpu, v);
            else if (t.contains("skin")) skin = max(skin, v);
            else if (t.contains("cpu") || t.contains("big") || t.contains("mid") || t.contains("little")
                    || t.contains("soc") || t.contains("tsens")) cpu = max(cpu, v);
        }
        cpuT = cpu;
        gpuT = gpu;
        skinT = skin;
    }

    private static String readLine(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            return r.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private static float max(float a, float b) {
        return Float.isNaN(a) ? b : Math.max(a, b);
    }

    private static float[] nan() {
        float[] a = new float[HISTORY];
        Arrays.fill(a, Float.NaN);
        return a;
    }

    private static void push(float[] h, float v) {
        System.arraycopy(h, 1, h, 0, h.length - 1);
        h[h.length - 1] = v;
    }
}
