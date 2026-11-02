package dev.nico.folio;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Hoja inferior propia (fondo oscurecido y panel que sube desde abajo). */
final class Sheet {
    interface Host {
        FrameLayout sheetRoot();

        int sheetBottomInset();

        void onSheetShown(Sheet s);

        void onSheetClosed(Sheet s);
    }

    final LinearLayout body;
    private final Host host;
    private final FrameLayout layer;
    private final View scrim;
    private final LinearLayout panel;
    private boolean cancelable = true;
    private boolean closing;
    private Runnable onClosed;

    Sheet(android.content.Context c, Host host) {
        this.host = host;
        layer = new FrameLayout(c);
        scrim = new View(c);
        scrim.setBackgroundColor(0xA6000000);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cancelable) dismiss();
            }
        });
        layer.addView(scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        float r = Ui.dp(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        panel.setClickable(true);
        panel.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), Ui.dp(20));

        View handle = new View(c);
        handle.setBackground(Ui.rounded(Ui.STROKE, 2));
        LinearLayout.LayoutParams hlp = Ui.lin(Ui.dp(36), Ui.dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dp(18);
        panel.addView(handle, hlp);

        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        panel.addView(body, Ui.lin(Ui.MATCH, Ui.WRAP));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        layer.addView(panel, plp);
    }

    Sheet cancelable(boolean c) {
        cancelable = c;
        return this;
    }

    boolean isCancelable() {
        return cancelable;
    }

    Sheet onClosed(Runnable r) {
        onClosed = r;
        return this;
    }

    Sheet title(String s) {
        TextView t = Ui.text(body.getContext(), s, 22, Ui.TEXT, 650);
        body.addView(t, Ui.lin(Ui.MATCH, Ui.WRAP));
        return this;
    }

    Sheet subtitle(String s) {
        TextView t = Ui.text(body.getContext(), s, 14, Ui.TEXT_2, 400);
        t.setLineSpacing(Ui.dp(3), 1f);
        LinearLayout.LayoutParams lp = Ui.lin(Ui.MATCH, Ui.WRAP);
        lp.topMargin = Ui.dp(6);
        body.addView(t, lp);
        return this;
    }

    Sheet space(int dp) {
        body.addView(new View(body.getContext()), Ui.lin(1, Ui.dp(dp)));
        return this;
    }

    Sheet add(View v) {
        body.addView(v, Ui.lin(Ui.MATCH, Ui.WRAP));
        return this;
    }

    /** Fila de acción: icono, texto, descripción opcional. Cierra la hoja al tocarla. */
    Sheet action(int icon, String label, String detail, boolean danger, final Runnable r) {
        android.content.Context c = body.getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(60));
        row.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        row.setBackground(Ui.pressable(Ui.rounded(0x00000000, 16), 16, 0x1FFFFFFF));
        row.setClickable(true);
        int color = danger ? Ui.DANGER : Ui.TEXT;

        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.rounded(Ui.SURFACE_2, 13));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new Icon(icon, color, 20));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        badge.addView(iv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        LinearLayout.LayoutParams blp = Ui.lin(Ui.dp(40), Ui.dp(40));
        blp.rightMargin = Ui.dp(14);
        row.addView(badge, blp);

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(c, label, 16, color, 550);
        texts.addView(t);
        if (detail != null) {
            TextView d = Ui.text(c, detail, 13, Ui.TEXT_2, 400);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dlp = Ui.lin(Ui.WRAP, Ui.WRAP);
            dlp.topMargin = Ui.dp(4);
            texts.addView(d, dlp);
        }
        row.addView(texts, Ui.lin(0, Ui.WRAP, 1f));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (r != null) r.run();
            }
        });
        LinearLayout.LayoutParams lp = Ui.lin(Ui.MATCH, Ui.WRAP);
        lp.leftMargin = -Ui.dp(8);
        lp.rightMargin = -Ui.dp(8);
        body.addView(row, lp);
        return this;
    }

    void clear() {
        body.removeAllViews();
    }

    void show() {
        FrameLayout root = host.sheetRoot();
        root.addView(layer, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        updateLayout();
        host.onSheetShown(this);
        scrim.animate().alpha(1f).setDuration(220).start();
        panel.setVisibility(View.INVISIBLE);
        panel.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                panel.getViewTreeObserver().removeOnPreDrawListener(this);
                panel.setTranslationY(panel.getHeight());
                panel.setVisibility(View.VISIBLE);
                panel.animate().translationY(0f).setDuration(340)
                        .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
                return false;
            }
        });
    }

    /** Ajusta el ancho (máx. 560 dp) y el hueco inferior (barra de gestos o teclado). */
    void updateLayout() {
        ViewGroup root = host.sheetRoot();
        int width = root.getWidth() > 0 ? root.getWidth() : root.getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        plp.width = Math.min(width, Ui.dp(560));
        panel.setLayoutParams(plp);
        panel.setPadding(panel.getPaddingLeft(), panel.getPaddingTop(), panel.getPaddingRight(),
                host.sheetBottomInset() + Ui.dp(20));
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        scrim.animate().alpha(0f).setDuration(200).start();
        panel.animate().translationY(Math.max(panel.getHeight(), Ui.dp(200))).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        ViewGroup parent = (ViewGroup) layer.getParent();
                        if (parent != null) parent.removeView(layer);
                        host.onSheetClosed(Sheet.this);
                        if (onClosed != null) onClosed.run();
                    }
                }).start();
    }

    boolean isClosing() {
        return closing;
    }
}
