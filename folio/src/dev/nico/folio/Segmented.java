package dev.nico.folio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Selector de opciones con un indicador que se desliza. */
final class Segmented extends LinearLayout {
    interface OnChange {
        void onChange(int index);
    }

    private final Paint indicator = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final TextView[] items;
    private final OnChange onChange;
    private int selected;
    private float indLeft = -1, indRight = -1;
    private ValueAnimator anim;

    Segmented(Context c, String[] labels, int selected, OnChange onChange) {
        super(c);
        this.onChange = onChange;
        this.selected = Math.max(0, Math.min(labels.length - 1, selected));
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        setBackground(Ui.rounded(Ui.SURFACE_2, 16));
        int p = Ui.dp(4);
        setPadding(p, p, p, p);
        indicator.setColor(Ui.SURFACE_3);
        items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.text(c, labels[i], 14, Ui.TEXT_2, 600);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx);
                }
            });
            addView(t, Ui.lin(0, Ui.dp(42), 1f));
            items[i] = t;
        }
        refreshColors();
    }

    int selected() {
        return selected;
    }

    private void select(int idx) {
        if (idx == selected) return;
        selected = idx;
        refreshColors();
        animateIndicator();
        if (onChange != null) onChange.onChange(idx);
    }

    private void refreshColors() {
        for (int i = 0; i < items.length; i++) items[i].setTextColor(i == selected ? Ui.TEXT : Ui.TEXT_2);
    }

    private void animateIndicator() {
        TextView t = items[selected];
        final float fromL = indLeft, fromR = indRight;
        final float toL = t.getLeft(), toR = t.getRight();
        if (anim != null) anim.cancel();
        if (fromL < 0) {
            indLeft = toL;
            indRight = toR;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(240);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float f = (Float) va.getAnimatedValue();
                indLeft = fromL + (toL - fromL) * f;
                indRight = fromR + (toR - fromR) * f;
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (anim == null || !anim.isRunning()) {
            indLeft = items[selected].getLeft();
            indRight = items[selected].getRight();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (indRight <= indLeft) return;
        TextView t = items[selected];
        rect.set(indLeft, t.getTop(), indRight, t.getBottom());
        float r = Ui.dp(12);
        c.drawRoundRect(rect, r, r, indicator);
    }
}
