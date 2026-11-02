package dev.nico.folio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Paleta, medidas y piezas de interfaz reutilizables. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0B0B0E;
    static final int SURFACE = 0xFF141418;
    static final int SURFACE_2 = 0xFF1C1C22;
    static final int SURFACE_3 = 0xFF272730;
    static final int STROKE = 0xFF2E2E38;
    static final int TEXT = 0xFFF4F4F6;
    static final int TEXT_2 = 0xFF9C9CA8;
    static final int TEXT_3 = 0xFF66666F;
    static final int ACCENT = 0xFFD7FF64;
    static final int ON_ACCENT = 0xFF0B0B0E;
    static final int DANGER = 0xFFFF6B6B;

    static final int PRIMARY = 0;
    static final int SECONDARY = 1;
    static final int GHOST = 2;

    private static float density = 1f;

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static Typeface font(int weight) {
        return Typeface.create(Typeface.SANS_SERIF, weight, false);
    }

    static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    static GradientDrawable rounded(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = rounded(color, radiusDp);
        d.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return d;
    }

    static Drawable pressable(Drawable content, float radiusDp, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, rounded(0xFFFFFFFF, radiusDp));
    }

    static TextView text(Context c, CharSequence s, float sp, int color, int weight) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font(weight));
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, s, 13, TEXT_2, 500);
        t.setLetterSpacing(0.02f);
        return t;
    }

    /** Botón en forma de píldora con icono opcional. */
    static LinearLayout button(Context c, String label, int icon, int style) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(56));
        b.setPadding(dp(20), 0, dp(22), 0);
        b.setClickable(true);
        b.setFocusable(true);
        ImageView iv = new ImageView(c);
        iv.setTag("icon");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(20));
        ilp.rightMargin = dp(10);
        b.addView(iv, ilp);
        TextView t = text(c, label, 16, TEXT, 600);
        t.setTag("label");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        b.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        style(b, label, icon, style);
        return b;
    }

    static void style(LinearLayout b, String label, int icon, int style) {
        ImageView iv = (ImageView) b.findViewWithTag("icon");
        TextView t = (TextView) b.findViewWithTag("label");
        int fg;
        Drawable bg;
        if (style == PRIMARY) {
            fg = ON_ACCENT;
            bg = pressable(rounded(ACCENT, 28), 28, 0x33000000);
        } else if (style == SECONDARY) {
            fg = TEXT;
            bg = pressable(rounded(SURFACE_2, 28, STROKE, 1), 28, 0x22FFFFFF);
        } else {
            fg = TEXT_2;
            bg = pressable(rounded(0x00000000, 28), 28, 0x22FFFFFF);
        }
        b.setBackground(bg);
        t.setText(label);
        t.setTextColor(fg);
        t.setVisibility(label == null || label.isEmpty() ? View.GONE : View.VISIBLE);
        if (icon != 0) {
            iv.setImageDrawable(new Icon(icon, fg, 20));
            iv.setVisibility(View.VISIBLE);
            ((LinearLayout.LayoutParams) iv.getLayoutParams()).rightMargin = t.getVisibility() == View.VISIBLE ? dp(10) : 0;
        } else {
            iv.setVisibility(View.GONE);
        }
        b.setContentDescription(label);
    }

    /** Botón redondo solo con icono. */
    static ImageView iconButton(Context c, int icon, int color, String description) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new Icon(icon, color, 22));
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setBackground(pressable(rounded(0x00000000, 24), 24, 0x22FFFFFF));
        v.setClickable(true);
        v.setFocusable(true);
        v.setContentDescription(description);
        return v;
    }

    static LinearLayout.LayoutParams lin(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lin(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
}
