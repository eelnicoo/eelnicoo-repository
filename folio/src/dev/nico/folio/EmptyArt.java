package dev.nico.folio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Ilustración del estado vacío: dos hojas y un «+». */
final class EmptyArt extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Icon photo = new Icon(Icon.PHOTOS, Ui.TEXT_3, 34);
    private final Icon plus = new Icon(Icon.PLUS, Ui.ON_ACCENT, 20, 2.4f);

    EmptyArt(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dp(1));
        stroke.setColor(Ui.STROKE);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Ui.dp(170), Ui.dp(150));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float w = Ui.dp(82), h = Ui.dp(106), r = Ui.dp(14);

        c.save();
        c.rotate(-10, cx - Ui.dp(18), cy);
        rect.set(cx - Ui.dp(18) - w / 2, cy - h / 2, cx - Ui.dp(18) + w / 2, cy + h / 2);
        fill.setColor(Ui.SURFACE);
        c.drawRoundRect(rect, r, r, fill);
        c.drawRoundRect(rect, r, r, stroke);
        c.restore();

        c.save();
        c.rotate(7, cx + Ui.dp(14), cy);
        rect.set(cx + Ui.dp(14) - w / 2, cy - h / 2, cx + Ui.dp(14) + w / 2, cy + h / 2);
        fill.setColor(Ui.SURFACE_2);
        c.drawRoundRect(rect, r, r, fill);
        c.drawRoundRect(rect, r, r, stroke);
        int s = Ui.dp(34);
        int px = (int) rect.centerX(), py = (int) rect.centerY();
        photo.setBounds(px - s / 2, py - s / 2, px + s / 2, py + s / 2);
        photo.draw(c);
        c.restore();

        float br = Ui.dp(19);
        float bx = cx + Ui.dp(56), by = cy + Ui.dp(46);
        fill.setColor(Ui.BG);
        c.drawCircle(bx, by, br + Ui.dp(4), fill);
        fill.setColor(Ui.ACCENT);
        c.drawCircle(bx, by, br, fill);
        int ps = Ui.dp(20);
        plus.setBounds((int) (bx - ps / 2f), (int) (by - ps / 2f), (int) (bx + ps / 2f), (int) (by + ps / 2f));
        plus.draw(c);
    }
}
