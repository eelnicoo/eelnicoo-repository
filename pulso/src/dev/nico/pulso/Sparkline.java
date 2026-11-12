package dev.nico.pulso;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;

/** Gráfica del último minuto con dos series (FPS o consumo, y temperatura). */
final class Sparkline extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint lineA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = Ui.paint(10f, Ui.TEXT3, Ui.SEMIBOLD);
    private final TextPaint legend = Ui.paint(10.5f, Ui.TEXT2, Ui.SEMIBOLD);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final int heightPx;

    private float[] a, b;
    private String aName = "FPS", bName = "CPU", aNow = "—", bNow = "—";
    private float aMax = 120f, bMin = 20f, bMax = 90f;
    private int aColor = Mode.BALANCED.c1, bColor = Ui.AMBER;

    Sparkline(Context c, int heightDp) {
        super(c);
        heightPx = Ui.dp(heightDp);
        bg.setColor(Ui.TILE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, Ui.dpf(1)));
        stroke.setColor(Ui.TILE_STROKE);
        grid.setColor(0x0DFFFFFF);
        grid.setStrokeWidth(Math.max(1f, Ui.dpf(0.75f)));
        lineA.setStyle(Paint.Style.STROKE);
        lineA.setStrokeWidth(Ui.dpf(2));
        lineA.setStrokeCap(Paint.Cap.ROUND);
        lineA.setStrokeJoin(Paint.Join.ROUND);
        lineB.setStyle(Paint.Style.STROKE);
        lineB.setStrokeWidth(Ui.dpf(1.5f));
        lineB.setStrokeCap(Paint.Cap.ROUND);
        lineB.setStrokeJoin(Paint.Join.ROUND);
        title.setLetterSpacing(0.08f);
        legend.setFontFeatureSettings("tnum");
    }

    void set(float[] a, String aName, String aNow, float aMax, int aColor,
             float[] b, String bName, String bNow, float bMin, float bMax) {
        this.a = a;
        this.aName = aName;
        this.aNow = aNow;
        this.aMax = aMax;
        this.aColor = aColor;
        this.b = b;
        this.bName = bName;
        this.bNow = bNow;
        this.bMin = bMin;
        this.bMax = bMax;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(300) : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, heightPx);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float radius = Ui.dpf(16), pad = Ui.dpf(12);
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        cv.drawRoundRect(r, radius, radius, bg);
        cv.drawRoundRect(r, radius, radius, stroke);

        float headY = pad + Ui.dpf(5);
        cv.drawText("ÚLTIMO MINUTO", pad, Ui.baseline(title, headY), title);

        // Leyenda, de derecha a izquierda.
        float x = w - pad;
        String tb = bName + " " + bNow;
        x -= legend.measureText(tb);
        legend.setColor(Ui.TEXT2);
        cv.drawText(tb, x, Ui.baseline(legend, headY), legend);
        x -= Ui.dpf(9);
        dot.setColor(bColor);
        cv.drawCircle(x, headY, Ui.dpf(3), dot);
        x -= Ui.dpf(12);
        String ta = aName + " " + aNow;
        x -= legend.measureText(ta);
        cv.drawText(ta, x, Ui.baseline(legend, headY), legend);
        x -= Ui.dpf(9);
        dot.setColor(aColor);
        cv.drawCircle(x, headY, Ui.dpf(3), dot);

        float left = pad, right = w - pad, top = headY + Ui.dpf(12), bottom = h - pad;
        if (bottom - top < Ui.dpf(10)) return;
        float third = (bottom - top) / 3f;
        cv.drawLine(left, top + third, right, top + third, grid);
        cv.drawLine(left, top + 2 * third, right, top + 2 * third, grid);

        lineB.setColor(Ui.alpha(bColor, 0.9f));
        series(cv, b, bMin, bMax, left, top, right, bottom, lineB, null);
        lineA.setColor(aColor);
        fillA.setShader(new LinearGradient(0, top, 0, bottom, Ui.alpha(aColor, 0.32f), Ui.alpha(aColor, 0f), Shader.TileMode.CLAMP));
        series(cv, a, 0f, aMax, left, top, right, bottom, lineA, fillA);
    }

    /** Dibuja una serie, por tramos (los NaN cortan la línea), suavizada con curvas. */
    private void series(Canvas cv, float[] v, float min, float max, float left, float top, float right, float bottom,
                        Paint line, Paint fill) {
        if (v == null || v.length < 2 || max <= min) return;
        int n = v.length;
        float step = (right - left) / (n - 1);
        int i = 0;
        while (i < n) {
            while (i < n && Float.isNaN(v[i])) i++;
            int start = i;
            while (i < n && !Float.isNaN(v[i])) i++;
            int end = i - 1;
            if (end <= start) continue;
            path.reset();
            float px = 0, py = 0;
            for (int k = start; k <= end; k++) {
                float xx = left + k * step;
                float yy = bottom - Ui.clamp01((v[k] - min) / (max - min)) * (bottom - top);
                if (k == start) {
                    path.moveTo(xx, yy);
                } else {
                    float mx = (px + xx) / 2f;
                    path.cubicTo(mx, py, mx, yy, xx, yy);
                }
                px = xx;
                py = yy;
            }
            if (fill != null) {
                Path f = new Path(path);
                f.lineTo(left + end * step, bottom);
                f.lineTo(left + start * step, bottom);
                f.close();
                cv.drawPath(f, fill);
            }
            cv.drawPath(path, line);
        }
    }
}
