package dev.nico.pulso;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

/** Píldora del HUD: «● 60 FPS | CPU 52° | 4,2 W | 78 %». */
final class HudView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = Ui.paint(11.5f, Ui.TEXT, Ui.SEMIBOLD);
    private final RectF r = new RectF();
    private String[] parts = {"Pulso"};
    private int[] colors = {Ui.TEXT};
    private int accent = Mode.BALANCED.c1;

    HudView(Context c) {
        super(c);
        bg.setColor(0xF00B0D12);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, Ui.dpf(1)));
        stroke.setColor(0x26FFFFFF);
        sep.setColor(0x2EFFFFFF);
        sep.setStrokeWidth(Math.max(1f, Ui.dpf(1)));
        text.setFontFeatureSettings("tnum");
    }

    void setAccent(int c) {
        accent = c;
        invalidate();
    }

    void update(Stats s) {
        String a = Stats.ok(s.fps) ? Ui.f0(s.fps) + " FPS" : (s.shizuku ? "— FPS" : Ui.f0(s.refresh) + " Hz");
        String b;
        int bc;
        if (Stats.ok(s.cpuTemp)) {
            b = "CPU " + Ui.f0(s.cpuTemp) + "°";
            bc = Ui.severity(s.cpuTemp, 55f, 72f);
        } else {
            b = "BAT " + (Stats.ok(s.batteryTemp) ? Ui.f0(s.batteryTemp) : "—") + "°";
            bc = Ui.severity(s.batteryTemp, 38f, 43f);
        }
        String c = Stats.ok(s.power) ? (s.charging ? "+" : "") + Ui.f1(s.power) + " W" : null;
        String d = s.battery >= 0 ? s.battery + " %" : null;
        int n = 2 + (c != null ? 1 : 0) + (d != null ? 1 : 0);
        String[] p = new String[n];
        int[] col = new int[n];
        int i = 0;
        p[i] = a;
        col[i++] = Ui.TEXT;
        p[i] = b;
        col[i++] = bc;
        if (c != null) {
            p[i] = c;
            col[i++] = Ui.TEXT;
        }
        if (d != null) {
            p[i] = d;
            col[i] = s.battery <= 15 && !s.charging ? Ui.HOT : Ui.TEXT;
        }
        boolean relayout = width(p) != width(parts);
        parts = p;
        colors = col;
        if (relayout) requestLayout();
        invalidate();
    }

    private int width(String[] p) {
        float w = Ui.dpf(12) + Ui.dpf(6) + Ui.dpf(8); // margen + punto + hueco
        for (int i = 0; i < p.length; i++) {
            w += text.measureText(p[i]);
            if (i < p.length - 1) w += Ui.dpf(17);
        }
        return Math.round(w + Ui.dpf(12));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(width(parts), Ui.dp(26));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        cv.drawRoundRect(r, h / 2f, h / 2f, bg);
        cv.drawRoundRect(r, h / 2f, h / 2f, stroke);
        float x = Ui.dpf(12), cy = h / 2f;
        dot.setColor(accent);
        cv.drawCircle(x + Ui.dpf(3), cy, Ui.dpf(3), dot);
        x += Ui.dpf(6) + Ui.dpf(8);
        float base = Ui.baseline(text, cy);
        for (int i = 0; i < parts.length; i++) {
            text.setColor(colors[i]);
            cv.drawText(parts[i], x, base, text);
            x += text.measureText(parts[i]);
            if (i < parts.length - 1) {
                x += Ui.dpf(8);
                cv.drawLine(x, cy - Ui.dpf(5), x, cy + Ui.dpf(5), sep);
                x += Ui.dpf(9);
            }
        }
    }
}
