package dev.nico.pulso;

/** Una lectura de rendimiento. NaN = no disponible. */
final class Stats {
    int battery = -1;
    float batteryTemp = Float.NaN;
    float voltage = Float.NaN;
    float current = Float.NaN;
    float power = Float.NaN;
    boolean charging;

    long ramUsed;
    long ramTotal;

    float refresh;
    float maxRefresh;

    int thermal;
    float headroom = Float.NaN;

    boolean shizuku;
    float fps = Float.NaN;
    float cpuLoad = Float.NaN;
    float cpuGhz = Float.NaN;
    float cpuTemp = Float.NaN;
    float gpuTemp = Float.NaN;
    float skinTemp = Float.NaN;

    float[] histFps;
    float[] histPow;
    float[] histCpu;
    float[] histBat;

    static boolean ok(float v) {
        return !Float.isNaN(v);
    }

    static boolean any(float[] a) {
        if (a == null) return false;
        for (float v : a) {
            if (!Float.isNaN(v)) return true;
        }
        return false;
    }
}
