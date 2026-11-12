package dev.nico.pulso;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Paleta, tipografías y ayudas de dibujo comunes a la app, el panel y el HUD. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF06080C;
    static final int TEXT = 0xFFF3F5F9;
    static final int TEXT2 = 0xFFA3ABBC;
    static final int TEXT3 = 0xFF6B7386;
    static final int TILE = 0x10FFFFFF;
    static final int TILE_STROKE = 0x18FFFFFF;
    static final int CARD = 0xC20D1016;
    static final int CARD_STROKE = 0x16FFFFFF;
    static final int INK = 0xFF080A0F;
    static final int GOOD = 0xFF4BE3A4;
    static final int WARN = 0xFFFFC24F;
    static final int HOT = 0xFFFF5C5C;
    static final int AMBER = 0xFFFFB25B;

    static final Typeface REGULAR = Typeface.create(Typeface.SANS_SERIF, 400, false);
    static final Typeface MEDIUM = Typeface.create(Typeface.SANS_SERIF, 500, false);
    static final Typeface SEMIBOLD = Typeface.create(Typeface.SANS_SERIF, 600, false);
    static final Typeface BOLD = Typeface.create(Typeface.SANS_SERIF, 700, false);

    static final Locale ES = new Locale("es", "ES");
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static float dpf(float v) {
        return v * Resources.getSystem().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return Math.round(dpf(v));
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    static int alpha(int color, float a) {
        int al = Math.round(clamp01(a) * 255f);
        return (color & 0x00FFFFFF) | (al << 24);
    }

    static int mix(int from, int to, float t) {
        t = clamp01(t);
        int a = Math.round(((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Verde, ámbar o rojo según el valor. */
    static int severity(float v, float warn, float hot) {
        if (Float.isNaN(v)) return TEXT3;
        return v >= hot ? HOT : (v >= warn ? WARN : GOOD);
    }

    static String f0(float v) {
        return String.format(ES, "%.0f", v);
    }

    static String f1(float v) {
        return String.format(ES, "%.1f", v);
    }

    static String f2(float v) {
        return String.format(ES, "%.2f", v);
    }

    static GradientDrawable rect(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dpf(radiusDp));
        g.setColor(fill);
        return g;
    }

    static GradientDrawable rect(int fill, float radiusDp, int stroke) {
        GradientDrawable g = rect(fill, radiusDp);
        g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    static GradientDrawable gradient(int c1, int c2, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {c1, c2});
        g.setCornerRadius(dpf(radiusDp));
        return g;
    }

    static RippleDrawable ripple(Drawable content, float radiusDp, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, rect(0xFFFFFFFF, radiusDp));
    }

    static TextView text(Context c, CharSequence s, float sizeSp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    /** Texto con tamaño fijo en dp: el panel es compacto y no debe crecer con la escala de fuente. */
    static TextView textDp(Context c, CharSequence s, float sizeDp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextPaint paint(float sizeDp, int color, Typeface tf) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(dpf(sizeDp));
        p.setColor(color);
        p.setTypeface(tf);
        return p;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    /** Márgenes en px. */
    static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    static Drawable icon(Context c, int res, int color) {
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        return d;
    }

    static void drawIcon(Canvas cv, Drawable d, float cx, float cy, float size, int color) {
        int half = Math.round(size / 2f);
        int x = Math.round(cx), y = Math.round(cy);
        d.setBounds(x - half, y - half, x + half, y + half);
        d.setTint(color);
        d.draw(cv);
    }

    /** Línea base para centrar verticalmente un texto en cy. */
    static float baseline(Paint p, float cy) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return cy - (fm.ascent + fm.descent) / 2f;
    }

    static String fit(TextPaint p, String s, float width) {
        if (s == null) return "";
        return TextUtils.ellipsize(s, p, Math.max(0f, width), TextUtils.TruncateAt.END).toString();
    }
}
